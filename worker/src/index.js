import { DurableObject } from "cloudflare:workers";

export class Room extends DurableObject {
  constructor(ctx, env){ super(ctx, env); this.clients=new Set(); }
  async fetch(request){
    if(request.headers.get("Upgrade") !== "websocket") return new Response("WebSocket required",{status:426});
    const pair=new WebSocketPair(); const client=pair[0], server=pair[1]; server.accept();
    this.clients.add(server);
    server.addEventListener("close",()=>this.clients.delete(server));
    server.addEventListener("message",e=>{
      for(const c of this.clients){ if(c!==server && c.readyState===1) c.send(e.data); }
    });
    server.send(JSON.stringify({type:"room",room:this.ctx.id.toString().slice(0,8)}));
    return new Response(null,{status:101,webSocket:client});
  }
}

export default {
 async fetch(request,env){
  const u=new URL(request.url);
  if(!u.pathname.startsWith("/room/")) return new Response("PS3 DNS Tester signaling worker OK");
  let room=u.pathname.split("/")[2]||"default";
  if(room==="new") room=crypto.randomUUID().replaceAll("-","").slice(0,8);
  const id=env.ROOM.idFromName(room);
  return env.ROOM.get(id).fetch(request);
 }
};
