package com.ps3online.dnstester.p2p;
import org.json.JSONObject;
import android.content.Context;import com.ps3online.dnstester.stun.StunP2P;import okhttp3.*;import org.json.*;import java.net.*;import java.nio.*;import java.util.*;import java.util.concurrent.*;import java.util.function.Consumer;
public class P2PSession{
 final String wsUrl,initialRoom,dns,info;final boolean host;final Consumer<String> out;final Context ctx;WebSocket ws;String room;DatagramSocket udp;InetSocketAddress peer;long sent,recv;volatile boolean ready;
 public P2PSession(String u,String r,boolean h,String d,String i,Context c,Consumer<String> o){wsUrl=u;initialRoom=r;host=h;dns=d;info=i;ctx=c;out=o;}
 public void start()throws Exception{
  StunP2P.Result st=StunP2P.discover("stun.cloudflare.com",3478,6000);out.accept("STUN: "+st.message);if(!st.ok)throw new Exception("STUN no respondió");udp=st.socket;
  OkHttpClient c=new OkHttpClient();Request req=new Request.Builder().url(wsUrl+(initialRoom.isEmpty()?"/room/new":"/room/"+initialRoom)).build();
  ws=c.newWebSocket(req,new WebSocketListener(){
   public void onOpen(WebSocket w,Response r){ws=w;try{JSONObject x=new JSONObject();x.put("type","hello");x.put("room",initialRoom);x.put("role",host?"host":"join");x.put("endpoint",st.mapped.getAddress().getHostAddress()+":"+st.mapped.getPort());x.put("info",info);w.send(x.toString());}catch(Exception e){out.accept("SEÑAL: "+e.getMessage());}}
   public void onMessage(WebSocket w,String text){try{JSONObject x=new JSONObject(text);String t=x.optString("type");if(t.equals("room")){room=x.getString("room");out.accept("SALA: "+room+"  (usa este código en el otro móvil)");}
    if(t.equals("peer")){peer=new InetSocketAddress(x.getString("ip"),x.getInt("port"));out.accept("PEER: "+peer);startUdp();}
    if(t.equals("ping")){JSONObject y=new JSONObject();y.put("type","pong");w.send(y.toString());}
   }catch(Exception e){out.accept("SEÑAL inválida: "+e.getMessage());}}
   public void onFailure(WebSocket w,Throwable t,Response r){out.accept("WEBSOCKET: "+t.getMessage());}
  });
  Thread.sleep(1500);if(peer==null)out.accept("Esperando al segundo móvil...");
 }
 void startUdp(){if(ready)return;ready=true;new Thread(()->{try{
  long end=System.currentTimeMillis()+20000;byte[] b=new byte[256];udp.setSoTimeout(250);
  for(int i=0;i<100;i++){byte[] m=ByteBuffer.allocate(12).putLong(System.nanoTime()).putInt(i).array();udp.send(new DatagramPacket(m,m.length,peer));sent++;Thread.sleep(100);}
  while(System.currentTimeMillis()<end){try{DatagramPacket p=new DatagramPacket(b,b.length);udp.receive(p);recv++;}catch(SocketTimeoutException e){}}
  out.accept("P2P UDP REAL: enviados="+sent+" recibidos="+recv);
  out.accept("RESULTADO P2P: "+(recv>0?"PASS — hubo tráfico bidireccional":"NO DETERMINADO — no hubo respuesta"));
 }catch(Exception e){out.accept("UDP: "+e.getMessage());}}, "p2p-test").start();}
}
