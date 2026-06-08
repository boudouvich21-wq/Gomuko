import asyncio
import json
import websockets
import socket

games = {}
waiting_client = None
game_counter = 0

def check_win(board, player_val):
    for r in range(10):
        for c in range(10):
            if board[r][c] == player_val:
                # Horizontal
                if c <= 5 and all(board[r][c+i] == player_val for i in range(5)):
                    return True
                # Vertical
                if r <= 5 and all(board[r+i][c] == player_val for i in range(5)):
                    return True
                # Diagonal right
                if r <= 5 and c <= 5 and all(board[r+i][c+i] == player_val for i in range(5)):
                    return True
                # Diagonal left
                if r <= 5 and c >= 4 and all(board[r+i][c-i] == player_val for i in range(5)):
                    return True
    return False

async def handler(websocket):
    global waiting_client, game_counter

    try:
        init_message = await websocket.recv()
        init_data = json.loads(init_message)
        if init_data.get('type') != 'join':
            return
        player_name = init_data.get('name', 'Anonymous')
    except websockets.exceptions.ConnectionClosed:
        return

    current_game_id = None
    player_color = None

    if waiting_client is None:
        waiting_client = {'ws': websocket, 'name': player_name}
        player_color = 'black'
        current_game_id = game_counter
        game_counter += 1
        
        games[current_game_id] = {
            'players': {'black': websocket},
            'names': {'black': player_name, 'white': None},
            'board': [[0]*10 for _ in range(10)],
            'active': False
        }
        try:
            await websocket.send(json.dumps({
                "type": "init",
                "color": "black",
                "names": games[current_game_id]['names'],
                "msg": "Waiting for Player 2 to join..."
            }))
        except websockets.exceptions.ConnectionClosed:
            waiting_client = None
            del games[current_game_id]
            return
    else:
        player_color = 'white'
        current_game_id = game_counter - 1
        game = games[current_game_id]
        
        game['players']['white'] = websocket
        game['names']['white'] = player_name
        game['active'] = True
        
        waiting_client = None

        for color, ws_conn in game['players'].items():
            try:
                await ws_conn.send(json.dumps({
                    "type": "start",
                    "color": color,
                    "names": game['names'],
                    "msg": "Game Started! Place your stones."
                }))
            except websockets.exceptions.ConnectionClosed:
                game['active'] = False

    try:
        async for message in websocket:
            data = json.loads(message)
            game = games.get(current_game_id)

            if not game or not game['active'] or data.get('type') != 'move':
                continue

            r, c = int(data['row']), int(data['col'])

            # Validate move (ensure spot is empty)
            if game['board'][r][c] != 0:
                await websocket.send(json.dumps({"type": "error", "msg": "Spot already taken!"}))
                continue

            val = 1 if player_color == 'black' else 2
            game['board'][r][c] = val

            if check_win(game['board'], val):
                game['active'] = False
                win_msg = f"{game['names'][player_color]} Won!"
                for ws in game['players'].values():
                    try:
                        await ws.send(json.dumps({"type": "win", "board": game['board'], "msg": win_msg}))
                    except websockets.exceptions.ConnectionClosed:
                        pass
            else:
                for ws in game['players'].values():
                    try:
                        await ws.send(json.dumps({
                            "type": "update_board",
                            "board": game['board'],
                            "msg": "Game in progress..."
                        }))
                    except websockets.exceptions.ConnectionClosed:
                        pass
    except websockets.exceptions.ConnectionClosed:
        pass
    finally:
        if waiting_client and waiting_client['ws'] == websocket:
            waiting_client = None
        
        game = games.get(current_game_id)
        if game and game['active']:
            game['active'] = False
            opponent_color = 'white' if player_color == 'black' else 'black'
            opponent_ws = game['players'].get(opponent_color)
            if opponent_ws:
                try:
                    await opponent_ws.send(json.dumps({
                        "type": "error",
                        "msg": f"{player_name} disconnected. Game over."
                    }))
                except websockets.exceptions.ConnectionClosed:
                    pass

async def main():
    async with websockets.serve(handler, "0.0.0.0", 8765):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(('8.8.8.8', 80))
            local_ip = s.getsockname()[0]
        except Exception:
            local_ip = '127.0.0.1'
        finally:
            s.close()
        print(f"GoMoKu Server running concurrently on ws://{local_ip}:8765")
        await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(main())
