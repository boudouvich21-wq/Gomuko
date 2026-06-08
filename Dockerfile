FROM python:3.12-slim

WORKDIR /app

# Copy project files
COPY server.py .
COPY index.html .
COPY background.gif .
COPY blackStone.gif .
COPY whiteStone.gif .

# Install dependencies
RUN pip install --no-cache-dir websockets

# Expose the HTTP port (default 8000)
EXPOSE 8000

# Start the combined HTTP + WebSocket server
# HTTP on PORT, WebSocket on PORT+1
CMD python3 server.py

