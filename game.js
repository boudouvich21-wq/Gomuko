"use strict";

const BOARD_SIZE = 10;
const TURN_SECONDS = 30;
const RING_CIRCUMFERENCE = 169.65;

const state = {
    ws: null,
    username: "",
    myColor: null,
    names: { black: null, white: null },
    board: emptyBoard(),
    turn: null,
    status: "idle",
    result: null,
    gameId: null,
    lastMove: null,
    winningCells: [],
    latestMessage: "",
    latestType: "",
    turnSeconds: TURN_SECONDS,
    timerId: null,
    bannerTypingId: null,
    bannerTypingToken: 0,
    rematchRequested: false,
    intentionalClose: false,
};

const elements = {};

function emptyBoard() {
    return Array.from({ length: BOARD_SIZE }, () => Array(BOARD_SIZE).fill(0));
}

function cacheElements() {
    Object.assign(elements, {
        board: document.getElementById("board"),
        startScreen: document.getElementById("start-screen"),
        gameContainer: document.getElementById("game-container"),
        username: document.getElementById("username-input"),
        connect: document.getElementById("connect-btn"),
        banner: document.getElementById("status-banner"),
        bannerLabel: document.getElementById("banner-label"),
        bannerTimer: document.getElementById("banner-timer"),
        p1Card: document.getElementById("p1_card"),
        p2Card: document.getElementById("p2_card"),
        p1Name: document.getElementById("p1_player_name"),
        p2Name: document.getElementById("p2_player_name"),
        p1Color: document.getElementById("p1_player_color"),
        p2Color: document.getElementById("p2_player_color"),
        p1Timer: document.getElementById("p1_player_timer"),
        p2Timer: document.getElementById("p2_player_timer"),
        p1Ring: document.getElementById("p1_ring_fill"),
        p2Ring: document.getElementById("p2_ring_fill"),
        identity: document.getElementById("identity"),
        rematch: document.getElementById("rematch-btn"),
        leave: document.getElementById("leave-btn"),
        modal: document.getElementById("victory-overlay"),
        modalEyebrow: document.getElementById("victory-eyebrow"),
        modalTitle: document.getElementById("victory-title"),
        modalMessage: document.getElementById("victory-message"),
        modalRematch: document.getElementById("modal-rematch-btn"),
        modalLeave: document.getElementById("modal-leave-btn"),
        moveSound: document.getElementById("sound-move"),
        winSound: document.getElementById("sound-win"),
        toasts: document.getElementById("toast-container"),
    });
}

function connect() {
    const username = elements.username.value.trim();
    if (!username) {
        showToast("Please enter your name.", "error");
        elements.username.focus();
        return;
    }

    state.username = username;
    state.intentionalClose = false;
    elements.startScreen.style.display = "none";
    elements.gameContainer.style.display = "flex";
    setBanner("Connecting to the game server...");

    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    state.ws = new WebSocket(`${protocol}//${location.host}/ws`);
    state.ws.addEventListener("open", () => {
        send({ type: "join", name: state.username });
        setBanner("Connected. Joining matchmaking...");
    });
    state.ws.addEventListener("message", handleSocketMessage);
    state.ws.addEventListener("error", () => {
        showToast("Could not connect to the game server.", "error");
        setBanner("Connection error. Please try again.", "error");
    });
    state.ws.addEventListener("close", () => {
        stopTimer();
        if (!state.intentionalClose && !["finished", "abandoned"].includes(state.status)) {
            state.status = "connection_error";
            setBanner("Connection closed. Leave and join again.", "error");
            elements.board.classList.add("board-locked");
        }
    });
}

function handleSocketMessage(event) {
    let payload;
    try {
        payload = JSON.parse(event.data);
    } catch {
        showToast("The server sent an invalid response.", "error");
        return;
    }

    if (!["waiting", "start", "state", "error", "game_over", "rematch_pending"].includes(payload.type)) {
        return;
    }

    if (payload.type === "error") {
        applyServerSnapshot(payload, false);
        showToast(payload.message || "That action could not be completed.", "error");
        setBanner(payload.message || "Action rejected.", "error");
        window.setTimeout(renderStatus, 1800);
        return;
    }

    const previousBoard = state.board;
    const previousTurn = state.turn;
    const previousStatus = state.status;
    applyServerSnapshot(payload, true);

    const boardChanged = findChangedCell(previousBoard, state.board);
    if (!state.lastMove && boardChanged) {
        state.lastMove = boardChanged;
    }

    if (boardChanged && payload.type === "state") {
        playSound(elements.moveSound);
    }

    if (
        payload.type === "start" ||
        (state.status === "active" && (previousTurn !== state.turn || previousStatus !== "active"))
    ) {
        startTimer();
    } else if (state.status !== "active") {
        stopTimer();
    }

    if (payload.type === "start") {
        state.rematchRequested = false;
        hideVictoryModal();
    } else if (payload.type === "game_over") {
        if (state.status === "finished" && state.result !== "Draw") {
            playSound(elements.winSound);
        }
        showVictoryModal();
    } else if (payload.type === "rematch_pending") {
        showToast(payload.message || "Rematch requested.", "info");
    }

    renderAll();
}

function applyServerSnapshot(payload, updateMessage) {
    if (payload.names) {
        state.names = { ...state.names, ...payload.names };
    }
    if (payload.your_color) state.myColor = payload.your_color;
    if (Array.isArray(payload.board)) state.board = payload.board.map((row) => [...row]);
    if ("turn" in payload) state.turn = payload.turn;
    if (payload.status) state.status = payload.status;
    if ("result" in payload) state.result = payload.result;
    if ("game_id" in payload) state.gameId = payload.game_id;
    if ("last_move" in payload) state.lastMove = payload.last_move;
    if (Array.isArray(payload.winning_cells)) state.winningCells = payload.winning_cells;
    if (updateMessage) {
        state.latestMessage = payload.message || "";
        state.latestType = payload.type || "";
    }
}

function findChangedCell(previous, next) {
    if (!Array.isArray(previous) || !Array.isArray(next)) return null;
    for (let row = 0; row < BOARD_SIZE; row += 1) {
        for (let col = 0; col < BOARD_SIZE; col += 1) {
            if ((previous[row]?.[col] || 0) !== (next[row]?.[col] || 0)) {
                return { row, col };
            }
        }
    }
    return null;
}

function renderAll() {
    renderBoard();
    renderPlayers();
    renderStatus();
    renderIdentity();
    renderControls();
    updateTimerDisplay();
}

function renderBoard() {
    elements.board.replaceChildren();
    const canMove = state.status === "active" && state.turn === state.myColor;
    elements.board.classList.toggle("board-locked", !canMove);
    elements.board.classList.toggle("game-over", ["finished", "abandoned"].includes(state.status));

    for (let row = 0; row < BOARD_SIZE; row += 1) {
        for (let col = 0; col < BOARD_SIZE; col += 1) {
            const cell = document.createElement("button");
            cell.type = "button";
            cell.className = "cell";
            cell.dataset.row = String(row);
            cell.dataset.col = String(col);
            cell.setAttribute("aria-label", `Row ${row + 1}, column ${col + 1}`);

            const value = state.board[row]?.[col] || 0;
            if (value) {
                const stone = document.createElement("span");
                stone.className = `stone ${value === 1 ? "black" : "white"}`;
                if (isWinningCell(row, col)) stone.classList.add("win-highlight");
                if (state.lastMove?.row === row && state.lastMove?.col === col) {
                    stone.classList.add("last-stone", "placed-stone");
                }
                cell.appendChild(stone);
                cell.disabled = true;
            } else if (canMove) {
                cell.classList.add("cell-valid");
                const hint = document.createElement("span");
                hint.className = `cell-hint preview-${state.myColor}`;
                cell.appendChild(hint);
                cell.addEventListener("click", () => makeMove(row, col));
            } else {
                cell.disabled = true;
            }
            elements.board.appendChild(cell);
        }
    }

    renderWinningLine();
}

function renderWinningLine() {
    if (state.winningCells.length < 2) return;
    const first = state.winningCells[0];
    const last = state.winningCells[state.winningCells.length - 1];
    const x1 = ((first.col + 0.5) / BOARD_SIZE) * 100;
    const y1 = ((first.row + 0.5) / BOARD_SIZE) * 100;
    const x2 = ((last.col + 0.5) / BOARD_SIZE) * 100;
    const y2 = ((last.row + 0.5) / BOARD_SIZE) * 100;
    const width = Math.hypot(x2 - x1, y2 - y1);
    const angle = Math.atan2(y2 - y1, x2 - x1) * (180 / Math.PI);
    const line = document.createElement("div");
    line.className = "winning-line";
    line.style.left = `${x1}%`;
    line.style.top = `${y1}%`;
    line.style.width = `${width}%`;
    line.style.transform = `translateY(-50%) rotate(${angle}deg)`;
    elements.board.appendChild(line);
}

function isWinningCell(row, col) {
    return state.winningCells.some((cell) => cell.row === row && cell.col === col);
}

function renderPlayers() {
    elements.p1Name.textContent = state.names.black || "Waiting...";
    elements.p2Name.textContent = state.names.white || "Waiting...";
    elements.p1Color.textContent = state.names.black
        ? `${state.myColor === "black" ? "You - " : ""}Black Stone`
        : "";
    elements.p2Color.textContent = state.names.white
        ? `${state.myColor === "white" ? "You - " : ""}White Stone`
        : "";

    const activeBlack = state.status === "active" && state.turn === "black";
    const activeWhite = state.status === "active" && state.turn === "white";
    setCardState(elements.p1Card, activeBlack, state.status === "active" && !activeBlack);
    setCardState(elements.p2Card, activeWhite, state.status === "active" && !activeWhite);
}

function setCardState(card, active, inactive) {
    card.classList.toggle("active-card", active);
    card.classList.toggle("inactive-card", inactive);
}

function renderStatus() {
    if (state.status === "waiting") {
        setBanner("Waiting for opponent...");
        return;
    }
    if (state.status === "active" && state.turn) {
        if (state.turn === state.myColor) {
            setBanner("Your Turn - Choose a position", "active");
        } else {
            typeBanner(`${playerName(state.turn)} is thinking...`);
        }
        return;
    }
    if (state.latestType === "rematch_pending") {
        setBanner(state.latestMessage || "Waiting for both players to confirm the rematch.");
        return;
    }
    if (state.status === "finished") {
        setBanner(state.result === "Draw" ? "Game Over - Draw" : `Game Over - ${state.result || ""}`);
        return;
    }
    if (state.status === "abandoned") {
        setBanner(state.latestMessage || "Opponent disconnected - Game over", "error");
        return;
    }
    setBanner(state.latestMessage || "Connecting...");
}

function setBanner(text, variant = "") {
    cancelBannerTyping();
    styleBanner(variant);
    elements.bannerLabel.textContent = text;
}

function typeBanner(text) {
    cancelBannerTyping();
    styleBanner("opponent");

    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        elements.bannerLabel.textContent = text;
        return;
    }

    const token = state.bannerTypingToken;
    let index = 0;
    elements.bannerLabel.textContent = "";
    elements.bannerLabel.classList.add("banner-typing");

    state.bannerTypingId = window.setInterval(() => {
        if (token !== state.bannerTypingToken) return;
        index += 1;
        elements.bannerLabel.textContent = text.slice(0, index);
        if (index >= text.length) {
            window.clearInterval(state.bannerTypingId);
            state.bannerTypingId = null;
            elements.bannerLabel.classList.remove("banner-typing");
            elements.bannerLabel.classList.add("banner-thinking");
        }
    }, 55);
}

function cancelBannerTyping() {
    state.bannerTypingToken += 1;
    if (state.bannerTypingId) {
        window.clearInterval(state.bannerTypingId);
        state.bannerTypingId = null;
    }
    elements.bannerLabel?.classList.remove("banner-typing", "banner-thinking");
}

function styleBanner(variant = "") {
    elements.bannerLabel.className = "banner-label";
    elements.bannerLabel.style.color = "";
    elements.banner.className = "status-banner";
    if (variant === "active") {
        elements.banner.classList.add("banner-active");
        elements.bannerLabel.classList.add("banner-my-turn");
    } else if (variant === "opponent") {
        elements.bannerLabel.classList.add("banner-opponent-turn");
    } else if (variant === "error") {
        elements.bannerLabel.style.color = "#ef8b82";
    }
}

function renderIdentity() {
    if (!state.myColor) {
        elements.identity.textContent = "Finding your seat...";
        return;
    }
    const color = state.myColor === "black" ? "Black Stone" : "White Stone";
    elements.identity.textContent = `You are ${state.username} - ${color}`;
}

function renderControls() {
    const rematchAvailable = state.status === "finished";
    elements.rematch.disabled = !rematchAvailable || state.rematchRequested;
    elements.modalRematch.disabled = !rematchAvailable || state.rematchRequested;
    const label = state.rematchRequested ? "Waiting..." : "Rematch";
    elements.rematch.textContent = label;
    elements.modalRematch.textContent = label;
}

function startTimer() {
    stopTimer();
    state.turnSeconds = TURN_SECONDS;
    updateTimerDisplay();
    state.timerId = window.setInterval(() => {
        state.turnSeconds = Math.max(0, state.turnSeconds - 1);
        updateTimerDisplay();
        if (state.turnSeconds === 0) stopTimer(false);
    }, 1000);
}

function stopTimer(clearDisplay = true) {
    if (state.timerId) {
        window.clearInterval(state.timerId);
        state.timerId = null;
    }
    if (clearDisplay) {
        state.turnSeconds = TURN_SECONDS;
    }
}

function updateTimerDisplay() {
    const active = state.status === "active" ? state.turn : null;
    const formatted = formatTime(state.turnSeconds);
    elements.bannerTimer.textContent = active ? formatted : "";
    applyTimerClasses(elements.bannerTimer, state.turnSeconds, "banner");

    updatePlayerTimer("black", elements.p1Timer, elements.p1Ring, formatted, active);
    updatePlayerTimer("white", elements.p2Timer, elements.p2Ring, formatted, active);
}

function updatePlayerTimer(color, timer, ring, formatted, active) {
    const isActive = color === active;
    timer.textContent = isActive ? formatted : "--:--";
    applyTimerClasses(timer, state.turnSeconds, "player", isActive);
    const offset = isActive
        ? RING_CIRCUMFERENCE * (1 - state.turnSeconds / TURN_SECONDS)
        : RING_CIRCUMFERENCE;
    ring.style.strokeDashoffset = String(offset);
    ring.setAttribute("class", "timer-ring-fill");
    if (isActive && state.turnSeconds <= 5) ring.classList.add("ring-danger");
    else if (isActive && state.turnSeconds <= 10) ring.classList.add("ring-warning");
}

function applyTimerClasses(element, seconds, kind, enabled = true) {
    if (kind === "banner") {
        element.className = "banner-timer";
        if (!enabled) return;
        if (seconds <= 5) element.classList.add("bt-danger");
        else if (seconds <= 10) element.classList.add("bt-warning");
        else element.classList.add("bt-green");
        return;
    }
    element.className = "player-timer";
    if (!enabled) return;
    if (seconds <= 5) element.classList.add("timer-danger");
    else if (seconds <= 10) element.classList.add("timer-warning");
}

function formatTime(seconds) {
    return `00:${String(Math.max(0, seconds)).padStart(2, "0")}`;
}

function makeMove(row, col) {
    if (
        state.status !== "active" ||
        state.turn !== state.myColor ||
        state.board[row]?.[col] !== 0
    ) {
        return;
    }
    elements.board.classList.add("board-locked");
    send({ type: "move", row, col });
}

function requestRematch() {
    if (state.status !== "finished" || state.rematchRequested) return;
    state.rematchRequested = true;
    renderControls();
    send({ type: "rematch_request" });
    showToast("Rematch requested. Waiting for your opponent.", "info");
}

function leaveGame() {
    state.intentionalClose = true;
    send({ type: "leave" });
    state.ws?.close();
    stopTimer();
    hideVictoryModal();
    state.ws = null;
    state.myColor = null;
    state.names = { black: null, white: null };
    state.board = emptyBoard();
    state.turn = null;
    state.status = "idle";
    state.result = null;
    state.gameId = null;
    state.lastMove = null;
    state.winningCells = [];
    state.latestMessage = "";
    state.latestType = "";
    state.rematchRequested = false;
    elements.gameContainer.style.display = "none";
    elements.startScreen.style.display = "flex";
    elements.username.value = state.username;
    elements.username.focus();
}

function showVictoryModal() {
    const winner = winnerName();
    const isDraw = state.result === "Draw";
    const didWin = winner && winner === state.username;
    const abandoned = state.status === "abandoned";

    elements.modalEyebrow.textContent = abandoned ? "Match ended" : "Match complete";
    elements.modalTitle.textContent = isDraw ? "Draw" : didWin ? "Victory!" : "Game Over";
    elements.modalMessage.textContent = abandoned
        ? state.latestMessage
        : isDraw
            ? "The board is full. A beautifully balanced match."
            : didWin
                ? "You won the match."
                : `${winner || "Your opponent"} wins the match.`;
    elements.modal.classList.add("visible");
    elements.modal.setAttribute("aria-hidden", "false");
    if (didWin) launchConfetti();
}

function hideVictoryModal() {
    elements.modal.classList.remove("visible");
    elements.modal.setAttribute("aria-hidden", "true");
    document.querySelectorAll(".confetti-piece").forEach((piece) => piece.remove());
}

function winnerName() {
    if (!state.result || state.result === "Draw") return null;
    return state.result.replace(/\s+wins$/i, "");
}

function launchConfetti() {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const colors = ["#f2ce73", "#4f8f7b", "#fff7e8", "#b5483e"];
    for (let index = 0; index < 54; index += 1) {
        const piece = document.createElement("i");
        piece.className = "confetti-piece";
        piece.style.left = `${Math.random() * 100}vw`;
        piece.style.background = colors[index % colors.length];
        piece.style.setProperty("--drift", `${Math.round(Math.random() * 180 - 90)}px`);
        piece.style.animationDelay = `${Math.random() * 500}ms`;
        document.body.appendChild(piece);
        window.setTimeout(() => piece.remove(), 3000);
    }
}

function showToast(message, type = "info", duration = 3600) {
    const toast = document.createElement("div");
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    elements.toasts.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add("visible"));
    const remove = () => {
        toast.classList.remove("visible");
        toast.classList.add("dismissing");
        window.setTimeout(() => toast.remove(), 300);
    };
    const timeout = window.setTimeout(remove, duration);
    toast.addEventListener("click", () => {
        window.clearTimeout(timeout);
        remove();
    });
}

function playerName(color) {
    return state.names[color] || (color === "black" ? "Black" : "White");
}

function send(payload) {
    if (state.ws?.readyState === WebSocket.OPEN) {
        state.ws.send(JSON.stringify(payload));
    }
}

function playSound(audio) {
    if (!audio) return;
    audio.currentTime = 0;
    audio.play().catch(() => {});
}

function init() {
    cacheElements();
    elements.connect.addEventListener("click", connect);
    elements.username.addEventListener("keydown", (event) => {
        if (event.key === "Enter") connect();
    });
    elements.rematch.addEventListener("click", requestRematch);
    elements.modalRematch.addEventListener("click", requestRematch);
    elements.leave.addEventListener("click", leaveGame);
    elements.modalLeave.addEventListener("click", leaveGame);
    elements.username.focus();
}

document.addEventListener("DOMContentLoaded", init);
