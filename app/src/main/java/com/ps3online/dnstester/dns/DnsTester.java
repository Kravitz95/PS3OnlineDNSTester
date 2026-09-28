package com.ps3online.dnstester.dns;
import java.io.*;import java.net.*;import java.util.*;
public class DnsTester{
 public static class Result{public boolean ok;public long rttMs;public String message;Result(boolean o,long r,String m){ok=o;rttMs=r;message=m;}}
 public static Result queryA(String resolver,String name,int timeout){long st=System.nanoTime();try{
  byte[] q=packet(name);DatagramSocket s=new DatagramSocket();s.setSoTimeout(timeout);s.send(new DatagramPacket(q,q.length,InetAddress.getByName(resolver),53));
  byte[] b=new byte[2048];DatagramPacket p=new DatagramPacket(b,b.length);s.receive(p);s.close();long ms=(System.nanoTime()-st)/1000000;
  if(p.getLength()<12)return new Result(false,ms,"respuesta inválida");int flags=((b[2]&255)<<8)|(b[3]&255);int r=flags&15;return new Result(r==0,ms,r==0?"respuesta válida":"RCODE="+r);
 }catch(Exception e){return new Result(false,(System.nanoTime()-st)/1000000,e.getMessage());}}
 static byte[] packet(String n)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();int id=new Random().nextInt(65536);b.write(id>>8);b.write(id);b.write(1);b.write(0);b.write(0);b.write(1);b.write(new byte[6]);for(String x:n.split("\\.")){b.write(x.length());b.write(x.getBytes("UTF-8"));}b.write(0);b.write(0);b.write(1);b.write(0);b.write(1);return b.toByteArray();}
}
