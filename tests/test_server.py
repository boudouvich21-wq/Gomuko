import asyncio
import unittest

from aiohttp import ClientSession, WSMsgType, web

from server import BOARD_SIZE, GAME_SERVER_KEY, board_is_full, check_win, create_app


class BoardRulesTests(unittest.TestCase):
    def setUp(self):
        self.board = [[0 for _ in range(BOARD_SIZE)] for _ in range(BOARD_SIZE)]

    def test_horizontal_win(self):
        for col in range(5):
            self.board[4][col] = 1
        self.assertTrue(check_win(self.board, 1))

    def test_vertical_win(self):
        for row in range(5):
            self.board[row][7] = 2
        self.assertTrue(check_win(self.board, 2))

    def test_downward_diagonal_win(self):
        for offset in range(5):
            self.board[2 + offset][3 + offset] = 1
        self.assertTrue(check_win(self.board, 1))

    def test_upward_diagonal_win(self):
        for offset in range(5):
            self.board[1 + offset][8 - offset] = 2
        self.assertTrue(check_win(self.board, 2))

    def test_full_board(self):
        board = [
            [1 if (row + col) % 2 == 0 else 2 for col in range(BOARD_SIZE)]
            for row in range(BOARD_SIZE)
        ]
        self.assertTrue(board_is_full(board))


class WebSocketIntegrationTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.app = create_app()
        self.runner = web.AppRunner(self.app)
        await self.runner.setup()
        self.site = web.TCPSite(self.runner, "127.0.0.1", 0)
        await self.site.start()
        port = self.site._server.sockets[0].getsockname()[1]
        self.base_url = f"http://127.0.0.1:{port}"
        self.session = ClientSession()
        self.websockets = []

    async def asyncTearDown(self):
        for websocket in self.websockets:
            if not websocket.closed:
                await websocket.close()
        await self.session.close()
        await self.runner.cleanup()

    async def connect_player(self, name):
        websocket = await self.session.ws_connect(f"{self.base_url}/ws")
        self.websockets.append(websocket)
        await websocket.send_json({"type": "join", "name": name})
        return websocket

    async def receive_json(self, websocket):
        message = await websocket.receive(timeout=2)
        self.assertEqual(message.type, WSMsgType.TEXT)
        return message.json()

    async def start_game(self, black_name="Alice", white_name="Bob"):
        black = await self.connect_player(black_name)
        waiting = await self.receive_json(black)
        self.assertEqual(waiting["type"], "waiting")

        white = await self.connect_player(white_name)
        black_start = await self.receive_json(black)
        white_start = await self.receive_json(white)
        self.assertEqual(black_start["type"], "start")
        self.assertEqual(white_start["type"], "start")
        self.assertEqual(black_start["your_color"], "black")
        self.assertEqual(white_start["your_color"], "white")
        self.assertEqual(black_start["turn"], "black")
        return black, white, black_start

    async def test_health_and_index_use_same_port(self):
        async with self.session.get(f"{self.base_url}/health") as response:
            self.assertEqual(response.status, 200)
            self.assertEqual((await response.json())["status"], "ok")
        async with self.session.get(f"{self.base_url}/") as response:
            self.assertEqual(response.status, 200)
            self.assertIn("GoMoKu", await response.text())

    async def test_two_players_share_moves_and_turns_are_enforced(self):
        black, white, _ = await self.start_game()

        await black.send_json({"type": "move", "row": 0, "col": 0})
        black_state = await self.receive_json(black)
        white_state = await self.receive_json(white)
        self.assertEqual(black_state["board"][0][0], 1)
        self.assertEqual(white_state["board"][0][0], 1)
        self.assertEqual(black_state["turn"], "white")

        await black.send_json({"type": "move", "row": 0, "col": 1})
        error = await self.receive_json(black)
        self.assertEqual(error["type"], "error")
        self.assertEqual(error["board"][0][1], 0)

        await white.send_json({"type": "move", "row": 0, "col": 0})
        error = await self.receive_json(white)
        self.assertEqual(error["type"], "error")
        self.assertEqual(error["board"][0][0], 1)

        await white.send_json({"type": "move", "row": 12, "col": 0})
        error = await self.receive_json(white)
        self.assertEqual(error["type"], "error")
        self.assertEqual(error["turn"], "white")

    async def test_four_players_are_isolated_into_two_games(self):
        first_black, first_white, first_start = await self.start_game("A", "B")
        second_black, second_white, second_start = await self.start_game("C", "D")

        self.assertNotEqual(first_start["game_id"], second_start["game_id"])
        await first_black.send_json({"type": "move", "row": 3, "col": 3})
        await self.receive_json(first_black)
        first_white_state = await self.receive_json(first_white)
        self.assertEqual(first_white_state["board"][3][3], 1)

        server = self.app[GAME_SERVER_KEY]
        second_game = server.games[second_start["game_id"]]
        self.assertEqual(second_game.board[3][3], 0)
        self.assertFalse(second_black.closed)
        self.assertFalse(second_white.closed)

    async def test_win_is_broadcast_and_game_is_cleaned_up(self):
        black, white, start = await self.start_game()
        for col in range(4):
            await black.send_json({"type": "move", "row": 2, "col": col})
            await self.receive_json(black)
            await self.receive_json(white)
            await white.send_json({"type": "move", "row": 8, "col": col})
            await self.receive_json(black)
            await self.receive_json(white)

        await black.send_json({"type": "move", "row": 2, "col": 4})
        black_end = await self.receive_json(black)
        white_end = await self.receive_json(white)
        self.assertEqual(black_end["type"], "game_over")
        self.assertEqual(white_end["result"], "Alice wins")
        self.assertNotIn(start["game_id"], self.app[GAME_SERVER_KEY].games)

    async def test_last_move_can_end_in_a_draw(self):
        black, white, start = await self.start_game()
        game = self.app[GAME_SERVER_KEY].games[start["game_id"]]
        game.board = [
            [1 if ((col // 2 + row) % 2 == 0) else 2 for col in range(BOARD_SIZE)]
            for row in range(BOARD_SIZE)
        ]
        game.board[0][0] = 0
        game.turn = "black"

        await black.send_json({"type": "move", "row": 0, "col": 0})
        black_end = await self.receive_json(black)
        white_end = await self.receive_json(white)
        self.assertEqual(black_end["type"], "game_over")
        self.assertEqual(white_end["result"], "Draw")
        self.assertNotIn(start["game_id"], self.app[GAME_SERVER_KEY].games)

    async def test_waiting_and_active_disconnects_are_cleaned_up(self):
        waiting = await self.connect_player("Waiting")
        await self.receive_json(waiting)
        await waiting.close()
        await asyncio.sleep(0.05)
        self.assertEqual(len(self.app[GAME_SERVER_KEY].waiting_players), 0)

        black, white, start = await self.start_game("Stay", "Leave")
        await white.close()
        end = await self.receive_json(black)
        self.assertEqual(end["type"], "game_over")
        self.assertEqual(end["status"], "abandoned")
        self.assertNotIn(start["game_id"], self.app[GAME_SERVER_KEY].games)


if __name__ == "__main__":
    unittest.main()
