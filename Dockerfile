FROM python:3.12-slim

WORKDIR /app

# Install websockets library and curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir websockets

# Copy all project files
COPY server.py .
COPY index.html .
COPY background.gif .
COPY blackStone.gif .
COPY whiteStone.gif .

# Expose ports
EXPOSE 8080
EXPOSE 8081

# Start the server (HTTP on PORT=8080, WebSocket on PORT+1=8081)
CMD python3 server.py

