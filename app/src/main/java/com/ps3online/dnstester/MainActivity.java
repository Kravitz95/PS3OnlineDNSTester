package com.ps3online.dnstester;

import android.app.*;import android.os.*;import android.widget.*;import java.util.*;import java.util.concurrent.*;
import com.ps3online.dnstester.dns.DnsTester;import com.ps3online.dnstester.stun.StunP2P;import com.ps3online.dnstester.upnp.UpnpTester;import com.ps3online.dnstester.p2p.P2PSession;

public class MainActivity extends Activity {
 EditText dns,server,room; Spinner game; TextView log; Button solo,host,join;
 String[] games={"PS3/PSN general","Call of Duty: Black Ops II","Call of Duty: Ghosts","Call of Duty: Advanced Warfare","Call of Duty: Black Ops III","Grand Theft Auto IV"};
 public void onCreate(Bundle b){super.onCreate(b); LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(24,24,24,24);
 TextView h=new TextView(this);h.setText("PS3 Online DNS Tester 3.1\nPRUEBAS REALES");h.setTextSize(22);l.addView(h);
 dns=new EditText(this);dns.setHint("DNS elegido");dns.setText("1.1.1.1");l.addView(dns);
 game=new Spinner(this);game.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,games));l.addView(game);
 server=new EditText(this);server.setHint("WebSocket de señalización (wss://...)");l.addView(server);
 room=new EditText(this);room.setHint("Código de sala (Host: dejar vacío)");l.addView(room);
 solo=new Button(this);solo.setText("1. PRUEBA SOLO");l.addView(solo);
 host=new Button(this);host.setText("2. CREAR SALA P2P");l.addView(host);
 join=new Button(this);join.setText("3. UNIRSE A SALA P2P");l.addView(join);
 log=new TextView(this);log.setTextIsSelectable(true);ScrollView s=new ScrollView(this);s.addView(log);l.addView(s,new LinearLayout.LayoutParams(-1,0,1));setContentView(l);
 solo.setOnClickListener(v->solo());host.setOnClickListener(v->p2p(true));join.setOnClickListener(v->p2p(false));}
 void a(String x){runOnUiThread(()->log.append(x+"\n"));}
 void solo(){log.setText("");String d=dns.getText().toString().trim();String g=game.getSelectedItem().toString();Executors.newSingleThreadExecutor().execute(()->{
  try{a("JUEGO: "+g);DnsTester.Result dr=DnsTester.queryA(d,"example.com",4000);a("DNS directo: "+(dr.ok?"PASS":"FAIL")+" | "+dr.rttMs+" ms");
   StunP2P.Result sr=StunP2P.discover("stun.cloudflare.com",3478,5000);a("STUN/NAT: "+(sr.ok?"PASS":"NO DETERMINADO")+" | "+sr.message);
   UpnpTester.Result ur=UpnpTester.test(this,15000);a("UPnP: "+ur.status+" | "+ur.mapping+" | lease="+ur.leaseSeconds+"s | renovación="+ur.renewal+" | eliminación="+ur.removal);
   a("RESULTADO: "+(dr.ok&&sr.ok?"CANDIDATA PARA PASAR A PRUEBA P2P":"EVIDENCIA INSUFICIENTE / NO DETERMINADO"));
  }catch(Exception e){a("ERROR: "+e.getMessage());}});}
 void p2p(boolean create){log.setText("");String ws=server.getText().toString().trim();if(ws.isEmpty()){a("Falta URL WebSocket. Despliega worker/signaling del proyecto y coloca wss://...");return;}
  String code=room.getText().toString().trim();if(create)code="";String finalCode=code;String d=dns.getText().toString().trim();a(create?"CREANDO SALA...":"UNIENDO SALA "+code+"...");
  Executors.newSingleThreadExecutor().execute(()->{try{
   P2PSession s=new P2PSession(ws,finalCode,create,d,"game="+game.getSelectedItem().toString(),this,this::a);
  }catch(Exception e){a("P2P ERROR: "+e.getMessage());}});
 }
}
