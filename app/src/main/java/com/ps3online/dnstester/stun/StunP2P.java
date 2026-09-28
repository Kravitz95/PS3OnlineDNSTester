package com.ps3online.dnstester.stun;
import java.net.*;import java.util.*;
public class StunP2P{
 public static class Result{public boolean ok;public String message;public InetSocketAddress mapped;public DatagramSocket socket;Result(boolean o,String m,InetSocketAddress x,DatagramSocket s){ok=o;message=m;mapped=x;socket=s;}}
 public static Result discover(String host,int port,int timeout)throws Exception{
  DatagramSocket s=new DatagramSocket();s.setSoTimeout(timeout);byte[] req=new byte[20];req[1]=1;req[4]=0x21;req[5]=0x12;req[6]=(byte)0xA4;req[7]=0x42;byte[] tid=new byte[12];new Random().nextBytes(tid);System.arraycopy(tid,0,req,8,12);
  s.send(new DatagramPacket(req,20,InetAddress.getByName(host),port));byte[] b=new byte[2048];DatagramPacket p=new DatagramPacket(b,b.length);s.receive(p);
  InetSocketAddress mapped=parse(b,p.getLength());if(mapped==null){s.close();return new Result(false,"respuesta STUN sin XOR-MAPPED-ADDRESS",null,null);}
  return new Result(true,"endpoint público "+mapped.getAddress().getHostAddress()+":"+mapped.getPort(),mapped,s);
 }
 static InetSocketAddress parse(byte[] b,int len){try{int pos=20;while(pos+4<=len){int type=((b[pos]&255)<<8)|(b[pos+1]&255),n=((b[pos+2]&255)<<8)|(b[pos+3]&255);if(type==0x0020&&n>=8){int port=((b[pos+6]&255)<<8)|(b[pos+7]&255);port^=0x2112;byte[] ip=new byte[4];for(int i=0;i<4;i++)ip[i]=(byte)((b[pos+8+i]&255)^(new byte[]{0x21,0x12,(byte)0xA4,0x42}[i]));return new InetSocketAddress(InetAddress.getByAddress(ip),port);}pos+=4+n+(n%4==0?0:4-(n%4));} }catch(Exception e){}return null;}
}
