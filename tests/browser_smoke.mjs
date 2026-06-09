import fs from "node:fs/promises";

const CHROME_DEBUG_URL = process.env.CHROME_DEBUG_URL || "http://127.0.0.1:9222";
const APP_URL = process.env.APP_URL || "http://127.0.0.1:8082";
const OUTPUT_DIR = process.env.OUTPUT_DIR || "/tmp/gomoku-browser-smoke";

class CdpClient {
    constructor(targetId, webSocketUrl) {
        this.targetId = targetId;
        this.socket = new WebSocket(webSocketUrl);
        this.nextId = 1;
        this.pending = new Map();
        this.events = [];
    }

    async connect() {
        await new Promise((resolve, reject) => {
            this.socket.addEventListener("open", resolve, { once: true });
            this.socket.addEventListener("error", reject, { once: true });
        });
        this.socket.addEventListener("message", (event) => {
            const message = JSON.parse(event.data);
            if (message.id) {
                const pending = this.pending.get(message.id);
                if (!pending) return;
                this.pending.delete(message.id);
                if (message.error) pending.reject(new Error(message.error.message));
                else pending.resolve(message.result);
                return;
            }
            this.events.push(message);
        });
    }

    send(method, params = {}) {
        const id = this.nextId++;
        this.socket.send(JSON.stringify({ id, method, params }));
        return new Promise((resolve, reject) => {
            this.pending.set(id, { resolve, reject });
        });
    }

    async close() {
        await fetch(`${CHROME_DEBUG_URL}/json/close/${this.targetId}`).catch(() => {});
        this.socket.close();
    }
}

async function createPage() {
    const response = await fetch(`${CHROME_DEBUG_URL}/json/new?about:blank`, {
        method: "PUT",
    });
    if (!response.ok) throw new Error(`Unable to create Chrome tab: ${response.status}`);
    const target = await response.json();
    const client = new CdpClient(target.id, target.webSocketDebuggerUrl);
    await client.connect();
    await client.send("Page.enable");
    await client.send("Runtime.enable");
    await client.send("Log.enable");
    await client.send("DOM.enable");
    await client.send("CSS.enable");
    await client.send("Network.enable");
    await client.send("Network.setCacheDisabled", { cacheDisabled: true });
    return client;
}

async function evaluate(client, expression) {
    const result = await client.send("Runtime.evaluate", {
        expression,
        awaitPromise: true,
        returnByValue: true,
    });
    if (result.exceptionDetails) {
        throw new Error(result.exceptionDetails.exception?.description || "Browser evaluation failed");
    }
    return result.result.value;
}

async function waitFor(client, expression, timeoutMs = 8000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        if (await evaluate(client, expression)) return;
        await new Promise((resolve) => setTimeout(resolve, 100));
    }
    throw new Error(`Timed out waiting for: ${expression}`);
}

async function navigate(client, width, height) {
    await client.send("Emulation.setDeviceMetricsOverride", {
        width,
        height,
        deviceScaleFactor: 1,
        mobile: width < 600,
    });
    await client.send("Page.navigate", { url: `${APP_URL}?smoke=${Date.now()}` });
    await waitFor(client, "document.readyState === 'complete'");
    await waitFor(client, "typeof state === 'object' && document.querySelector('#connect-btn')");
}

async function join(client, name) {
    await evaluate(
        client,
        `(() => {
            document.querySelector("#username-input").value = ${JSON.stringify(name)};
            document.querySelector("#connect-btn").click();
            return true;
        })()`,
    );
}

async function move(client, row, col) {
    await waitFor(
        client,
        `state.status === "active" && state.turn === state.myColor &&
         document.querySelector('.cell[data-row="${row}"][data-col="${col}"].cell-valid')`,
    );
    await evaluate(
        client,
        `document.querySelector('.cell[data-row="${row}"][data-col="${col}"]').click()`,
    );
}

async function screenshot(client, filename) {
    const result = await client.send("Page.captureScreenshot", {
        format: "png",
        captureBeyondViewport: false,
    });
    await fs.writeFile(`${OUTPUT_DIR}/${filename}`, Buffer.from(result.data, "base64"));
}

function assert(condition, message) {
    if (!condition) throw new Error(message);
}

async function main() {
    await fs.mkdir(OUTPUT_DIR, { recursive: true });
    const black = await createPage();
    const white = await createPage();

    try {
        await Promise.all([navigate(black, 1440, 1000), navigate(white, 1440, 1000)]);
        await Promise.all([
            evaluate(
                black,
                `(() => {
                    window.__soundCalls = [];
                    HTMLMediaElement.prototype.play = function () {
                        window.__soundCalls.push(this.id);
                        return Promise.resolve();
                    };
                    return true;
                })()`,
            ),
            evaluate(
                white,
                `(() => {
                    window.__soundCalls = [];
                    HTMLMediaElement.prototype.play = function () {
                        window.__soundCalls.push(this.id);
                        return Promise.resolve();
                    };
                    return true;
                })()`,
            ),
        ]);
        await join(black, "MED");
        await waitFor(black, "state.status === 'waiting'");
        await join(white, "IKrame");
        await Promise.all([
            waitFor(black, "state.status === 'active' && state.names.white === 'IKrame'"),
            waitFor(white, "state.status === 'active' && state.names.black === 'MED'"),
        ]);
        await waitFor(
            white,
            `(() => {
                const label = document.querySelector("#banner-label");
                return label.classList.contains("banner-typing") &&
                    label.textContent.length > 0 &&
                    label.textContent.length < "MED is thinking...".length;
            })()`,
        );
        const typingPartial = await evaluate(
            white,
            `document.querySelector("#banner-label").textContent`,
        );
        await waitFor(
            white,
            `document.querySelector("#banner-label").textContent === "MED is thinking..." &&
             document.querySelector("#banner-label").classList.contains("banner-thinking")`,
        );
        const typingComplete = await evaluate(
            white,
            `({
                text: document.querySelector("#banner-label").textContent,
                thinking: document.querySelector("#banner-label").classList.contains("banner-thinking"),
            })`,
        );
        const documentNode = await black.send("DOM.getDocument");
        const hoverNode = await black.send("DOM.querySelector", {
            nodeId: documentNode.root.nodeId,
            selector: ".cell-valid",
        });
        await black.send("CSS.forcePseudoState", {
            nodeId: hoverNode.nodeId,
            forcedPseudoClasses: ["hover"],
        });
        await new Promise((resolve) => setTimeout(resolve, 400));

        const initial = await evaluate(
            black,
            `(() => {
                const board = document.querySelector("#board").getBoundingClientRect();
                const cell = document.querySelector(".cell").getBoundingClientRect();
                const hint = document.querySelector(".cell-valid .cell-hint").getBoundingClientRect();
                return {
                    banner: document.querySelector("#banner-label").textContent,
                    timer: document.querySelector("#banner-timer").textContent,
                    blackName: document.querySelector("#p1_player_name").textContent,
                    whiteName: document.querySelector("#p2_player_name").textContent,
                    boardWidth: board.width,
                    boardHeight: board.height,
                    hintRatio: hint.width / cell.width,
                    hintWidth: hint.width,
                    cellWidth: cell.width,
                    hintStyle: getComputedStyle(document.querySelector(".cell-valid .cell-hint")).width,
                    timerSeconds: Number(document.querySelector("#banner-timer").textContent.split(":")[1]),
                    gridColor: getComputedStyle(document.querySelector(".cell")).borderRightColor,
                    validCells: document.querySelectorAll(".cell-valid").length,
                    activeCard: document.querySelector("#p1_card").classList.contains("active-card"),
                    inactiveCard: document.querySelector("#p2_card").classList.contains("inactive-card"),
                };
            })()`,
        );
        console.log("Initial geometry:", initial);
        assert(initial.banner.includes("Your Turn"), "Current-turn banner is not above the board");
        assert(
            initial.timer.startsWith("00:") && initial.timerSeconds > 0 && initial.timerSeconds <= 30,
            "Turn timer is not visible or counting down",
        );
        assert(initial.blackName === "MED" && initial.whiteName === "IKrame", "Player names are unclear");
        assert(Math.abs(initial.boardWidth - initial.boardHeight) < 1, "Board is not square");
        await screenshot(black, "desktop-active.png");
        assert(
            initial.hintRatio >= 0.7 && initial.hintRatio <= 0.8,
            `Preview stone ratio is ${initial.hintRatio.toFixed(3)}, expected 0.70-0.80`,
        );
        assert(initial.validCells === 100, "Valid-cell hover state is not restricted correctly");
        assert(initial.activeCard && initial.inactiveCard, "Player active/inactive states are unclear");

        const timerStates = await evaluate(
            black,
            `(() => {
                state.turnSeconds = 9;
                updateTimerDisplay();
                const warning = {
                    banner: document.querySelector("#banner-timer").classList.contains("bt-warning"),
                    card: document.querySelector("#p1_player_timer").classList.contains("timer-warning"),
                    ring: document.querySelector("#p1_ring_fill").classList.contains("ring-warning"),
                };
                state.turnSeconds = 4;
                updateTimerDisplay();
                const danger = {
                    banner: document.querySelector("#banner-timer").classList.contains("bt-danger"),
                    card: document.querySelector("#p1_player_timer").classList.contains("timer-danger"),
                    ring: document.querySelector("#p1_ring_fill").classList.contains("ring-danger"),
                };
                state.turnSeconds = 30;
                updateTimerDisplay();
                return { warning, danger };
            })()`,
        );
        assert(Object.values(timerStates.warning).every(Boolean), "9-second warning state is incomplete");
        assert(Object.values(timerStates.danger).every(Boolean), "4-second danger state is incomplete");

        let placementState = null;
        for (let col = 0; col < 4; col += 1) {
            await move(black, 0, col);
            if (col === 0) {
                await waitFor(black, "state.board[0][0] === 1");
                placementState = await evaluate(
                    black,
                    `(() => {
                        const stone = document.querySelector(".stone.last-stone");
                        return {
                            highlighted: Boolean(stone),
                            animationName: stone ? getComputedStyle(stone).animationName : "",
                        };
                    })()`,
                );
                assert(placementState.highlighted, "Last placed stone is not highlighted");
                assert(
                    placementState.animationName.includes("stonePlace"),
                    "Ordinary stone placement has no landing animation",
                );
                await waitFor(
                    white,
                    `document.querySelector("#banner-label").textContent.includes("Your Turn")`,
                );
                const typingCancelled = await evaluate(
                    white,
                    `!document.querySelector("#banner-label").classList.contains("banner-typing") &&
                     !document.querySelector("#banner-label").classList.contains("banner-thinking")`,
                );
                assert(typingCancelled, "Thinking animation did not stop when the turn changed");
            }
            await move(white, 1, col);
        }
        await move(black, 0, 4);
        await Promise.all([
            waitFor(black, "state.status === 'finished' && state.winningCells.length === 5"),
            waitFor(white, "state.status === 'finished' && state.winningCells.length === 5"),
        ]);

        const victory = await evaluate(
            black,
            `(() => {
                const stone = document.querySelector(".stone.win-highlight").getBoundingClientRect();
                const cell = document.querySelector('.cell[data-row="0"][data-col="0"]').getBoundingClientRect();
                return {
                    modalVisible: document.querySelector("#victory-overlay").classList.contains("visible"),
                    title: document.querySelector("#victory-title").textContent,
                    message: document.querySelector("#victory-message").textContent,
                    winStones: document.querySelectorAll(".stone.win-highlight").length,
                    line: Boolean(document.querySelector(".winning-line")),
                    validCells: document.querySelectorAll(".cell-valid").length,
                    stoneRatio: stone.width / cell.width,
                    lastStone: Boolean(document.querySelector(".stone.last-stone")),
                    confettiCount: document.querySelectorAll(".confetti-piece").length,
                    soundCalls: window.__soundCalls,
                };
            })()`,
        );
        assert(victory.modalVisible, "Victory modal did not appear");
        assert(victory.title === "Victory!", "Winner-specific modal title is incorrect");
        assert(victory.message.includes("You won"), "Winner name/result is not clear");
        assert(victory.winStones === 5, "Exactly five winning stones are not highlighted");
        assert(victory.line, "Winning line is missing");
        assert(victory.validCells === 0, "Board still accepts moves after game over");
        assert(victory.stoneRatio >= 0.7 && victory.stoneRatio <= 0.8, "Placed stones do not fit inside cells");
        assert(victory.lastStone, "Last placed stone is not highlighted");
        assert(victory.confettiCount > 0, "Winner confetti was not created");
        assert(victory.soundCalls.includes("sound-move"), "Move sound was not invoked");
        assert(victory.soundCalls.includes("sound-win"), "Victory sound was not invoked");
        const loss = await evaluate(
            white,
            `({
                visible: document.querySelector("#victory-overlay").classList.contains("visible"),
                title: document.querySelector("#victory-title").textContent,
                message: document.querySelector("#victory-message").textContent,
            })`,
        );
        assert(loss.visible, "Losing player did not receive the result modal");
        assert(loss.title === "Game Over", "Losing player result title is incorrect");
        assert(loss.message.includes("MED wins"), "Losing player cannot see the winner name");
        await screenshot(black, "desktop-victory.png");
        await evaluate(
            black,
            `document.querySelector("#victory-overlay").style.display = "none"`,
        );
        await new Promise((resolve) => setTimeout(resolve, 100));
        await screenshot(black, "desktop-winning-board.png");
        await evaluate(
            black,
            `document.querySelector("#victory-overlay").style.display = ""`,
        );

        await black.send("Emulation.setDeviceMetricsOverride", {
            width: 390,
            height: 844,
            deviceScaleFactor: 1,
            mobile: true,
        });
        await new Promise((resolve) => setTimeout(resolve, 250));
        const mobile = await evaluate(
            black,
            `(() => {
                const board = document.querySelector("#board").getBoundingClientRect();
                return {
                    bodyWidth: document.documentElement.scrollWidth,
                    viewportWidth: window.innerWidth,
                    boardWidth: board.width,
                    boardHeight: board.height,
                    titleVisible: document.querySelector("#victory-title").getBoundingClientRect().height > 0,
                };
            })()`,
        );
        assert(mobile.bodyWidth <= mobile.viewportWidth + 1, "Mobile layout has horizontal overflow");
        assert(Math.abs(mobile.boardWidth - mobile.boardHeight) < 1, "Mobile board is not square");
        assert(mobile.boardWidth <= mobile.viewportWidth, "Mobile board exceeds the viewport");
        assert(mobile.titleVisible, "Victory result is hidden on mobile");
        await screenshot(black, "mobile-victory.png");

        await Promise.all([
            evaluate(black, `document.querySelector("#modal-rematch-btn").click()`),
            evaluate(white, `document.querySelector("#modal-rematch-btn").click()`),
        ]);
        await Promise.all([
            waitFor(black, "state.status === 'active' && state.board.flat().every((cell) => cell === 0)"),
            waitFor(white, "state.status === 'active' && state.board.flat().every((cell) => cell === 0)"),
        ]);
        const rematch = await evaluate(
            black,
            `({
                modalHidden: !document.querySelector("#victory-overlay").classList.contains("visible"),
                turn: state.turn,
                timer: document.querySelector("#banner-timer").textContent,
                stones: document.querySelectorAll(".stone").length,
            })`,
        );
        assert(rematch.modalHidden, "Victory modal did not close for the rematch");
        assert(rematch.turn === "black", "Rematch did not preserve Black as first turn");
        assert(rematch.timer.startsWith("00:"), "Rematch timer did not restart");
        assert(rematch.stones === 0, "Rematch board was not cleared");

        await evaluate(white, `document.querySelector("#leave-btn").click()`);
        await waitFor(black, "state.status === 'abandoned'");
        const abandoned = await evaluate(
            black,
            `({
                title: document.querySelector("#victory-title").textContent,
                message: document.querySelector("#victory-message").textContent,
                boardLocked: document.querySelector("#board").classList.contains("board-locked"),
            })`,
        );
        assert(abandoned.title === "Game Over", "Leave did not show a game-over result");
        assert(abandoned.message.includes("disconnected"), "Leave result does not explain what happened");
        assert(abandoned.boardLocked, "Board remained interactive after opponent left");

        const browserErrors = [...black.events, ...white.events].filter(
            (event) =>
                event.method === "Runtime.exceptionThrown" ||
                (event.method === "Log.entryAdded" && event.params.entry.level === "error"),
        );
        if (browserErrors.length) {
            console.error("Browser errors:", JSON.stringify(browserErrors, null, 2));
        }
        assert(browserErrors.length === 0, `Browser reported ${browserErrors.length} runtime error(s)`);

        console.log(
            JSON.stringify(
                {
                    initial,
                    typingPartial,
                    typingComplete,
                    timerStates,
                    placementState,
                    victory,
                    loss,
                    mobile,
                    rematch,
                    abandoned,
                    outputDir: OUTPUT_DIR,
                },
                null,
                2,
            ),
        );
    } finally {
        await Promise.all([black.close(), white.close()]);
    }
}

main().catch((error) => {
    console.error(error.stack || error.message);
    process.exitCode = 1;
});
