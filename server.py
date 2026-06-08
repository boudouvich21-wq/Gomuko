import asyncio
import json
import websockets
import os

games = {}
waiting_client = None
game_counter = 0

MIME_TYPES = {
    '.html': 'text/html',
    '.gif': 'image/gif',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.css': 'text/css',
    '.js': 'application/javascript',
    '.ico': 'image/x-icon',
    '.svg': 'image/svg+xml',
}

def check_win(board, player_val):
    for r in range(10):
        for c in range(10):
            if board[r][c] == player_val:
                if c <= 5 and all(board[r][c+i] == player_val for i in range(5)):
                    return True
                if r <= 5 and all(board[r+i][c] == player_val for i in range(5)):
                    return True
                if r <= 5 and c <= 5 and all(board[r+i][c+i] == player_val for i in range(5)):
                    return True
                if r <= 5 and c >= 4 and all(board[r+i][c-i] == player_val for i in range(5)):
                    return True
    return False

async def ws_handler(websocket):
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

async def http_handler(reader, writer):
    try:
        request_line = await reader.readline()
        if not request_line:
            writer.close()
            return
        
        while True:
            header_line = await reader.readline()
            if header_line == b'\r\n' or not header_line:
                break
        
        try:
            method, path, _ = request_line.decode().split()
        except:
            writer.close()
            return
        
        if path == '/':
            path = '/index.html'
        
        filepath = '.' + path
        
        if os.path.exists(filepath) and os.path.isfile(filepath):
            ext = os.path.splitext(filepath)[1].lower()
            mime = MIME_TYPES.get(ext, 'application/octet-stream')
            
            with open(filepath, 'rb') as f:
                body = f.read()
            
            response = (
                f'HTTP/1.1 200 OK\r\n'
                f'Content-Type: {mime}\r\n'
                f'Content-Length: {len(body)}\r\n'
                f'Access-Control-Allow-Origin: *\r\n'
                f'\r\n'
            ).encode() + body
        else:
            response = (
                f'HTTP/1.1 404 Not Found\r\n'
                f'Content-Type: text/plain\r\n'
                f'Content-Length: 9\r\n'
                f'\r\n'
                f'Not Found'
            ).encode()
        
        writer.write(response)
        await writer.drain()
    except:
        pass
    finally:
        writer.close()

async def main():
    port = int(os.environ.get("PORT", 8000))
    ws_port = port + 1
    
    print(f"Starting GoMoKu...")
    print(f"  - Web UI:     http://0.0.0.0:{port}")
    print(f"  - WebSocket:  ws://0.0.0.0:{ws_port}")
    
    http_server = await asyncio.start_server(http_handler, "0.0.0.0", port)
    
    async with websockets.serve(ws_handler, "0.0.0.0", ws_port):
        async with http_server:
            await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(main())
