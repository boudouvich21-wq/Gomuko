import asyncio
import itertools
import json
import os
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path

from aiohttp import WSMsgType, web


BOARD_SIZE = 10
ROOT = Path(__file__).resolve().parent
GAME_SERVER_KEY = web.AppKey("game_server", object)


def empty_board():
    return [[0 for _ in range(BOARD_SIZE)] for _ in range(BOARD_SIZE)]


def check_win(board, player_value):
    directions = ((0, 1), (1, 0), (1, 1), (1, -1))
    for row in range(BOARD_SIZE):
        for col in range(BOARD_SIZE):
            if board[row][col] != player_value:
                continue
            for row_step, col_step in directions:
                end_row = row + (4 * row_step)
                end_col = col + (4 * col_step)
                if not (0 <= end_row < BOARD_SIZE and 0 <= end_col < BOARD_SIZE):
                    continue
                if all(
                    board[row + offset * row_step][col + offset * col_step]
                    == player_value
                    for offset in range(5)
                ):
                    return True
    return False


def board_is_full(board):
    return all(cell != 0 for row in board for cell in row)


@dataclass
class Player:
    websocket: web.WebSocketResponse
    name: str
    color: str | None = None
    game_id: int | None = None


@dataclass
class Game:
    game_id: int
    players: dict[str, Player]
    board: list[list[int]] = field(default_factory=empty_board)
    turn: str = "black"
    status: str = "active"
    result: str | None = None


class GameServer:
    def __init__(self):
        self.waiting_players = deque()
        self.games = {}
        self.game_ids = itertools.count(1)
        self.matchmaking_lock = asyncio.Lock()

    def _state_payload(self, game, message, message_type="state"):
        return {
            "type": message_type,
            "game_id": game.game_id,
            "names": {
                color: game.players[color].name for color in ("black", "white")
            },
            "colors": {"black": 1, "white": 2},
            "board": game.board,
            "turn": game.turn if game.status == "active" else None,
            "status": game.status,
            "result": game.result,
            "message": message,
        }

    async def _send(self, player, payload):
        if not player.websocket.closed:
            await player.websocket.send_json(payload)

    async def _broadcast_state(self, game, message, message_type="state"):
        await asyncio.gather(
            *(
                self._send(
                    player,
                    {
                        **self._state_payload(game, message, message_type),
                        "your_color": color,
                    },
                )
                for color, player in game.players.items()
            ),
            return_exceptions=True,
        )

    def _remove_waiting_player(self, player):
        try:
            self.waiting_players.remove(player)
        except ValueError:
            pass

    def _next_available_waiting_player(self):
        while self.waiting_players:
            player = self.waiting_players.popleft()
            if not player.websocket.closed and player.game_id is None:
                return player
        return None

    async def join(self, player):
        async with self.matchmaking_lock:
            opponent = self._next_available_waiting_player()
            if opponent is None:
                player.color = "black"
                self.waiting_players.append(player)
                await self._send(
                    player,
                    {
                        "type": "waiting",
                        "game_id": None,
                        "names": {"black": player.name, "white": None},
                        "colors": {"black": 1, "white": 2},
                        "board": empty_board(),
                        "turn": None,
                        "status": "waiting",
                        "result": None,
                        "your_color": "black",
                        "message": "Waiting for a second player...",
                    },
                )
                return

            game_id = next(self.game_ids)
            opponent.color = "black"
            opponent.game_id = game_id
            player.color = "white"
            player.game_id = game_id
            game = Game(
                game_id=game_id,
                players={"black": opponent, "white": player},
            )
            self.games[game_id] = game
            await self._broadcast_state(
                game,
                f"Game started. {opponent.name} (Black) moves first.",
                "start",
            )

    async def send_error(self, player, message):
        game = self.games.get(player.game_id)
        if game:
            payload = self._state_payload(game, message, "error")
            payload["your_color"] = player.color
        else:
            payload = {
                "type": "error",
                "game_id": None,
                "names": {"black": player.name, "white": None},
                "colors": {"black": 1, "white": 2},
                "board": empty_board(),
                "turn": None,
                "status": "waiting",
                "result": None,
                "your_color": player.color,
                "message": message,
            }
        await self._send(player, payload)

    async def move(self, player, data):
        game = self.games.get(player.game_id)
        if not game or game.status != "active":
            await self.send_error(player, "This game is no longer active.")
            return

        if player.color != game.turn:
            await self.send_error(
                player,
                f"Please wait. It is {game.players[game.turn].name}'s turn.",
            )
            return

        row = data.get("row")
        col = data.get("col")
        if (
            isinstance(row, bool)
            or isinstance(col, bool)
            or not isinstance(row, int)
            or not isinstance(col, int)
            or not 0 <= row < BOARD_SIZE
            or not 0 <= col < BOARD_SIZE
        ):
            await self.send_error(player, "Move coordinates must be between 0 and 9.")
            return

        if game.board[row][col] != 0:
            await self.send_error(player, "That square is already occupied.")
            return

        player_value = 1 if player.color == "black" else 2
        game.board[row][col] = player_value

        if check_win(game.board, player_value):
            game.status = "finished"
            game.result = f"{player.name} wins"
            await self._broadcast_state(
                game,
                f"{player.name} wins with five stones in a row!",
                "game_over",
            )
            self.games.pop(game.game_id, None)
            return

        if board_is_full(game.board):
            game.status = "finished"
            game.result = "Draw"
            await self._broadcast_state(
                game,
                "The board is full. The game is a draw.",
                "game_over",
            )
            self.games.pop(game.game_id, None)
            return

        game.turn = "white" if game.turn == "black" else "black"
        next_player = game.players[game.turn]
        await self._broadcast_state(
            game,
            f"{next_player.name}'s turn ({game.turn.title()}).",
        )

    async def disconnect(self, player):
        async with self.matchmaking_lock:
            self._remove_waiting_player(player)

        game = self.games.pop(player.game_id, None)
        if not game or game.status != "active":
            return

        game.status = "abandoned"
        game.result = f"{player.name} disconnected"
        opponent_color = "white" if player.color == "black" else "black"
        opponent = game.players.get(opponent_color)
        if opponent and not opponent.websocket.closed:
            payload = self._state_payload(
                game,
                f"{player.name} disconnected. The game has ended.",
                "game_over",
            )
            payload["your_color"] = opponent.color
            await self._send(opponent, payload)


async def index(_request):
    return web.FileResponse(ROOT / "index.html")


async def static_asset(request):
    filename = request.match_info["filename"]
    if filename not in {"background.gif", "blackStone.gif", "whiteStone.gif"}:
        raise web.HTTPNotFound()
    return web.FileResponse(ROOT / filename)


async def health(_request):
    return web.json_response({"status": "ok", "service": "gomoku"})


async def websocket_handler(request):
    websocket = web.WebSocketResponse(heartbeat=30)
    await websocket.prepare(request)

    game_server = request.app[GAME_SERVER_KEY]
    player = None

    try:
        first_message = await websocket.receive()
        if first_message.type != WSMsgType.TEXT:
            await websocket.close(code=1002, message=b"Join message required")
            return websocket

        try:
            join_data = json.loads(first_message.data)
        except json.JSONDecodeError:
            await websocket.send_json(
                {"type": "error", "message": "Messages must be valid JSON."}
            )
            await websocket.close(code=1003)
            return websocket

        name = join_data.get("name")
        if join_data.get("type") != "join" or not isinstance(name, str):
            await websocket.send_json(
                {"type": "error", "message": "A valid join message is required."}
            )
            await websocket.close(code=1002)
            return websocket

        name = name.strip()
        if not name or len(name) > 40:
            await websocket.send_json(
                {
                    "type": "error",
                    "message": "Player name must contain 1 to 40 characters.",
                }
            )
            await websocket.close(code=1008)
            return websocket

        player = Player(websocket=websocket, name=name)
        await game_server.join(player)

        async for message in websocket:
            if message.type == WSMsgType.TEXT:
                try:
                    data = json.loads(message.data)
                except json.JSONDecodeError:
                    await game_server.send_error(player, "Messages must be valid JSON.")
                    continue

                if data.get("type") != "move":
                    await game_server.send_error(player, "Unsupported message type.")
                    continue
                await game_server.move(player, data)
            elif message.type == WSMsgType.ERROR:
                break
    finally:
        if player:
            await game_server.disconnect(player)

    return websocket


def create_app():
    app = web.Application()
    app[GAME_SERVER_KEY] = GameServer()
    app.router.add_get("/", index)
    app.router.add_get("/health", health)
    app.router.add_get("/ws", websocket_handler)
    app.router.add_get("/{filename}", static_asset)
    return app


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8080"))
    print(f"GoMoKu server listening on http://0.0.0.0:{port}")
    print(f"WebSocket endpoint: /ws | Health endpoint: /health")
    web.run_app(create_app(), host="0.0.0.0", port=port, print=None)
