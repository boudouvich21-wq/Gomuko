FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl

# Copy all project files
COPY GoMoKuServer.java .
COPY GoMoKuClient.java .
COPY GoMoKuApplet.java .
COPY index.html .
COPY manifest.txt .
COPY background.gif .
COPY blackStone.gif .
COPY whiteStone.gif .

# Compile all Java files
RUN javac GoMoKuServer.java GoMoKuClient.java GoMoKuApplet.java

# Create JAR with manifest (runnable client)
RUN jar cfm gomoku.jar manifest.txt GoMoKuClient.class GoMoKuClient\$BoardPanel.class GoMoKuApplet.class GoMoKuServer*.class
# Expose ports
EXPOSE 8765
EXPOSE 8080

# Start the server (game on 8765, HTTP on 8080)
CMD java GoMoKuServer 8765 8080

