import argparse
import asyncio
from urllib.parse import urlparse, urlunparse

from aiohttp import ClientSession, ClientWSTimeout, WSMsgType


def websocket_url(base_url):
    parsed = urlparse(base_url)
    scheme = "wss" if parsed.scheme == "https" else "ws"
    return urlunparse((scheme, parsed.netloc, "/ws", "", "", ""))


async def receive_json(websocket, expected_type):
    message = await websocket.receive(timeout=10)
    if message.type != WSMsgType.TEXT:
        raise RuntimeError(f"Expected a text message, received {message.type.name}")
    payload = message.json()
    if payload.get("type") != expected_type:
        raise RuntimeError(
            f"Expected message type {expected_type!r}, received {payload!r}"
        )
    return payload


async def verify(base_url):
    base_url = base_url.rstrip("/")
    timeout = 15
    websocket_timeout = ClientWSTimeout(ws_receive=timeout, ws_close=timeout)

    async with ClientSession() as session:
        async with session.get(f"{base_url}/health", timeout=timeout) as response:
            response.raise_for_status()
            health = await response.json()
            if health.get("status") != "ok":
                raise RuntimeError(f"Unexpected health response: {health!r}")
            print("[PASS] Health endpoint")

        async with session.get(base_url, timeout=timeout) as response:
            response.raise_for_status()
            page = await response.text()
            if "GoMoKu" not in page or "/ws" not in page:
                raise RuntimeError("The deployed page is not the repaired GoMoKu client.")
            print("[PASS] Browser client")

        ws_url = websocket_url(base_url)
        first = await session.ws_connect(
            ws_url, heartbeat=20, timeout=websocket_timeout
        )
        second = None
        try:
            await first.send_json({"type": "join", "name": "Deployment Test Black"})
            waiting = await receive_json(first, "waiting")
            if waiting.get("your_color") != "black":
                raise RuntimeError(f"Unexpected waiting state: {waiting!r}")
            print("[PASS] First player enters matchmaking")

            second = await session.ws_connect(
                ws_url, heartbeat=20, timeout=websocket_timeout
            )
            await second.send_json({"type": "join", "name": "Deployment Test White"})
            black_start = await receive_json(first, "start")
            white_start = await receive_json(second, "start")
            if (
                black_start.get("game_id") != white_start.get("game_id")
                or black_start.get("your_color") != "black"
                or white_start.get("your_color") != "white"
                or black_start.get("turn") != "black"
            ):
                raise RuntimeError("The two clients were not paired into one game.")
            print("[PASS] Two players pair into one game")

            await first.send_json({"type": "move", "row": 0, "col": 0})
            black_state = await receive_json(first, "state")
            white_state = await receive_json(second, "state")
            if (
                black_state["board"][0][0] != 1
                or white_state["board"][0][0] != 1
                or black_state.get("turn") != "white"
            ):
                raise RuntimeError("The valid move was not synchronized correctly.")
            print("[PASS] Move synchronizes to both players")

            await first.send_json({"type": "move", "row": 0, "col": 1})
            error = await receive_json(first, "error")
            if error["board"][0][1] != 0 or error.get("turn") != "white":
                raise RuntimeError("The out-of-turn move changed the game state.")
            print("[PASS] Invalid consecutive move is rejected")
        finally:
            if second is not None:
                await second.close()
            await first.close()

    print("\nDeployment verification completed successfully.")


def main():
    parser = argparse.ArgumentParser(
        description="Verify a deployed Internet GoMoKu application."
    )
    parser.add_argument(
        "url",
        help="Public application URL, for example https://gomoku.example.com",
    )
    args = parser.parse_args()
    parsed = urlparse(args.url)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        parser.error("URL must start with http:// or https:// and include a host.")
    asyncio.run(verify(args.url))


if __name__ == "__main__":
    main()
