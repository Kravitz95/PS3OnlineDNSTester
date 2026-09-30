const http = require("http");
const crypto = require("crypto");
const WebSocket = require("ws");

const PORT = process.env.PORT || 10000;

const server = http.createServer((req, res) => {

    if (req.url === "/health") {
        res.writeHead(200, {
            "Content-Type": "application/json"
        });

        res.end(JSON.stringify({
            status: "ok",
            service: "PS3 Online Connect Signaling",
            rooms: rooms.size
        }));

        return;
    }

    res.writeHead(200, {
        "Content-Type": "text/plain; charset=utf-8"
    });

    res.end("PS3 Online Connect Signaling Server");
});

const wss = new WebSocket.Server({
    server
});

const rooms = new Map();

function generateRoomCode() {

    const chars =
        "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    let code = "";

    for (let i = 0; i < 6; i++) {
        code += chars[
            crypto.randomInt(0, chars.length)
        ];
    }

    return code;
}

function createRoom() {

    let code;

    do {
        code = generateRoomCode();
    } while (rooms.has(code));

    rooms.set(code, {
        host: null,
        guest: null
    });

    return code;
}

function send(ws, object) {

    if (
        ws &&
        ws.readyState === WebSocket.OPEN
    ) {
        ws.send(JSON.stringify(object));
    }
}

function closeRoom(roomCode) {

    const room = rooms.get(roomCode);

    if (!room) {
        return;
    }

    if (room.host) {
        room.host.roomCode = null;
    }

    if (room.guest) {
        room.guest.roomCode = null;
    }

    rooms.delete(roomCode);
}

wss.on("connection", (ws) => {

    ws.roomCode = null;
    ws.role = null;

    send(ws, {
        type: "connected",
        message: "Señalización conectada"
    });

    ws.on("message", (raw) => {

        try {

            const message =
                JSON.parse(raw.toString());

            const type =
                message.type;

            /*
             * CREAR SALA
             */
            if (type === "create") {

                if (ws.roomCode) {
                    return;
                }

                const roomCode =
                    createRoom();

                const room =
                    rooms.get(roomCode);

                room.host = ws;

                ws.roomCode = roomCode;
                ws.role = "host";

                send(ws, {
                    type: "room",
                    room: roomCode,
                    role: "host"
                });

                return;
            }

            /*
             * UNIRSE A SALA
             */
            if (type === "join") {

                const roomCode =
                    String(message.room || "")
                        .trim()
                        .toUpperCase();

                const room =
                    rooms.get(roomCode);

                if (!room) {

                    send(ws, {
                        type: "error",
                        message: "La sala no existe."
                    });

                    return;
                }

                if (room.guest) {

                    send(ws, {
                        type: "error",
                        message: "La sala ya está ocupada."
                    });

                    return;
                }

                room.guest = ws;

                ws.roomCode = roomCode;
                ws.role = "guest";

                send(ws, {
                    type: "room",
                    room: roomCode,
                    role: "guest"
                });

                send(room.host, {
                    type: "peer_joined",
                    role: "guest"
                });

                return;
            }

            /*
             * HELLO / INFORMACIÓN DE CONECTIVIDAD
             */
            if (type === "hello") {

                if (!ws.roomCode) {
                    return;
                }

                const room =
                    rooms.get(ws.roomCode);

                if (!room) {
                    return;
                }

                ws.endpoint =
                    message.endpoint || "";

                ws.info =
                    message.info || "";

                const peer =
                    ws.role === "host"
                        ? room.guest
                        : room.host;

                if (!peer) {
                    return;
                }

                if (!peer.endpoint) {
                    return;
                }

                /*
                 * Intercambiamos únicamente la
                 * información necesaria para
                 * intentar el UDP P2P.
                 */

                send(ws, {
                    type: "peer",
                    ip: peer.endpoint.split(":")[0],
                    port: Number(
                        peer.endpoint.split(":").pop()
                    ),
                    info: peer.info || ""
                });

                send(peer, {
                    type: "peer",
                    ip: ws.endpoint.split(":")[0],
                    port: Number(
                        ws.endpoint.split(":").pop()
                    ),
                    info: ws.info || ""
                });

                return;
            }

            /*
             * PING DE SEÑALIZACIÓN
             */
            if (type === "ping") {

                send(ws, {
                    type: "pong"
                });

                return;
            }

            /*
             * MENSAJES DE CHAT / SEÑALIZACIÓN
             *
             * El servidor solamente retransmite.
             * No almacena el contenido.
             */
            if (
                type === "chat" ||
                type === "signal"
            ) {

                if (!ws.roomCode) {
                    return;
                }

                const room =
                    rooms.get(ws.roomCode);

                if (!room) {
                    return;
                }

                const peer =
                    ws.role === "host"
                        ? room.guest
                        : room.host;

                if (!peer) {
                    return;
                }

                send(peer, {
                    type: type,
                    message:
                        String(
                            message.message || ""
                        ).slice(0, 1000)
                });

                return;
            }

        } catch (error) {

            send(ws, {
                type: "error",
                message: "Mensaje de señalización inválido."
            });
        }
    });

    ws.on("close", () => {

        const roomCode =
            ws.roomCode;

        if (!roomCode) {
            return;
        }

        const room =
            rooms.get(roomCode);

        if (!room) {
            return;
        }

        const peer =
            ws.role === "host"
                ? room.guest
                : room.host;

        if (peer) {

            send(peer, {
                type: "peer_left",
                message: "El otro móvil salió de la sala."
            });
        }

        closeRoom(roomCode);
    });

    ws.on("error", () => {
        /*
         * El cierre posterior se encarga
         * de limpiar la sala.
         */
    });
});

const heartbeat =
    setInterval(() => {

        wss.clients.forEach((ws) => {

            if (ws.isAlive === false) {
                ws.terminate();
                return;
            }

            ws.isAlive = false;
            ws.ping();
        });

    }, 30000);

wss.on("connection", (ws) => {
    ws.isAlive = true;

    ws.on("pong", () => {
        ws.isAlive = true;
    });
});

wss.on("close", () => {
    clearInterval(heartbeat);
});

server.listen(
    PORT,
    "0.0.0.0",
    () => {

        console.log(
            `PS3 Online Connect Signaling Server activo en puerto ${PORT}`
        );
    }
);
