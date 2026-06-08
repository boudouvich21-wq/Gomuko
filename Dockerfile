FROM openjdk:21-slim

WORKDIR /app

# Copy Java source files
COPY GoMoKuServer.java .
COPY GoMoKuApplet.java .
COPY index.html .
COPY background.gif .
COPY blackStone.gif .
COPY whiteStone.gif .

# Compile Java files
RUN javac GoMoKuServer.java GoMoKuApplet.java

# Create JAR for the applet
RUN jar cf gomoku.jar GoMoKuApplet.class GoMoKuServer*.class

# Expose the game server port
EXPOSE 8765

# Start the game server
CMD java GoMoKuServer 8765
