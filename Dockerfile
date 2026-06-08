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

# Expose the WebSocket port (default 8765)
EXPOSE 8765

# Start just the WebSocket server
# The HTML files need to be served separately or via a separate HTTP server
CMD python3 server.py

