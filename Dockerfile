FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Copy all project files
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

# Expose ports
EXPOSE 8765
EXPOSE 8080

# Start the server (game on 8765, HTTP on 8080)
CMD java GoMoKuServer 8765 8080

