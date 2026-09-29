import { DurableObject } from "cloudflare:workers";

export class Room extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.clients = new Map();
    this.endpoints = new Map();
  }

  async fetch(request) {
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("WebSocket required", { status: 426 });
    }

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];

    server.accept();

    const clientId = crypto.randomUUID();

    this.clients.set(clientId, server);

    server.addEventListener("close", () => {
      this.clients.delete(clientId);
      this.endpoints.delete(clientId);
    });

    server.addEventListener("message", (event) => {
      try {
        const msg = JSON.parse(event.data);

        if (msg.type === "hello") {
          this.endpoints.set(clientId, {
            ip: msg.ip || null,
            port: msg.port || null,
            role: msg.role || "unknown"
          });

          server.send(JSON.stringify({
            type: "hello_ok",
            message: "Señalización conectada"
          }));

          // Enviar a cada participante el endpoint del otro.
          for (const [otherId, otherClient] of this.clients) {
            if (otherId === clientId) continue;

            const otherEndpoint = this.endpoints.get(otherId);

            if (
              otherEndpoint &&
              otherEndpoint.ip &&
              otherEndpoint.port &&
              msg.ip &&
              msg.port
            ) {
              otherClient.send(JSON.stringify({
                type: "peer",
                ip: msg.ip,
                port: msg.port,
                role: msg.role
              }));

              server.send(JSON.stringify({
                type: "peer",
                ip: otherEndpoint.ip,
                port: otherEndpoint.port,
                role: otherEndpoint.role
              }));
            }
          }

          return;
        }

        if (msg.type === "ping") {
          server.send(JSON.stringify({
            type: "pong"
          }));
        }

      } catch (e) {
        server.send(JSON.stringify({
          type: "error",
          message: "Mensaje inválido"
        }));
      }
    });

    server.send(JSON.stringify({
      type: "room",
      room: this.ctx.id.toString().slice(0, 8)
    }));

    return new Response(null, {
      status: 101,
      webSocket: client
    });
  }
}

export default {
  async fetch(request, env) {
    const u = new URL(request.url);

    if (!u.pathname.startsWith("/room/")) {
      return new Response(
        "PS3 DNS Tester signaling worker OK"
      );
    }

    let room = u.pathname.split("/")[2] || "default";

    if (room === "new") {
      room = crypto.randomUUID()
        .replaceAll("-", "")
        .slice(0, 8);
    }

    const id = env.ROOM.idFromName(room);

    return env.ROOM.get(id).fetch(request);
  }
};
