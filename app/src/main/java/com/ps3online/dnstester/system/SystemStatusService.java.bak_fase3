package com.ps3online.dnstester.system;

import android.content.Context;

import com.ps3online.dnstester.stun.StunP2P;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class SystemStatusService {

    public enum State {
        OK,
        DEGRADED,
        ERROR,
        UNKNOWN
    }

    public static class Item {
        public final String name;
        public final String description;
        public final State state;
        public final String detail;

        public Item(
                String name,
                String description,
                State state,
                String detail
        ) {
            this.name = name;
            this.description = description;
            this.state = state;
            this.detail = detail;
        }
    }

    public interface Callback {
        void onComplete(List<Item> items);
    }

    private static final String SIGNALING_HTTP =
            "https://ps3-online-connect-signaling.onrender.com/";

    private static final String SIGNALING_WS =
            "wss://ps3-online-connect-signaling.onrender.com";

    private static final String WIKIDATA_API =
            "https://www.wikidata.org/w/api.php?action=wbsearchentities"
            + "&search=PlayStation%203"
            + "&language=en"
            + "&format=json"
            + "&limit=1";

    private static final String STUN_HOST =
            "stun.cloudflare.com";

    private static final int STUN_PORT = 3478;

    private final OkHttpClient httpClient =
            new OkHttpClient.Builder()
                    .build();

    public void checkAll(Context context, Callback callback) {

        new Thread(() -> {

            List<Item> items = new ArrayList<>();

            items.add(checkLocalDatabase(context));
            items.add(checkDnsComponent());
            items.add(checkWikidata());
            items.add(checkSignalingHttp());
            items.add(checkWebSocket());
            items.add(checkStun());
            items.add(checkUpnp());

            callback.onComplete(items);

        }).start();
    }

    private Item checkLocalDatabase(Context context) {

        try (InputStream input =
                     context.getAssets().open("game_profiles.json")) {

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    input,
                                    StandardCharsets.UTF_8
                            )
                    );

            int lines = 0;
            while (reader.readLine() != null) {
                lines++;
            }

            if (lines > 0) {
                return new Item(
                        "🗃️ Base de datos de juegos",
                        "game_profiles.json",
                        State.OK,
                        "Archivo cargado correctamente"
                );
            }

            return new Item(
                    "🗃️ Base de datos de juegos",
                    "game_profiles.json",
                    State.ERROR,
                    "Archivo vacío"
            );

        } catch (Exception e) {

            return new Item(
                    "🗃️ Base de datos de juegos",
                    "game_profiles.json",
                    State.ERROR,
                    "No se pudo cargar"
            );
        }
    }

    private Item checkDnsComponent() {

        try {

            Class.forName(
                    "com.ps3online.dnstester.dns.DnsTester"
            );

            return new Item(
                    "🌐 Motor DNS",
                    "DnsTester",
                    State.OK,
                    "Componente disponible"
            );

        } catch (Exception e) {

            return new Item(
                    "🌐 Motor DNS",
                    "DnsTester",
                    State.ERROR,
                    "Componente no disponible"
            );
        }
    }

    private Item checkWikidata() {

        HttpURLConnection connection = null;

        try {

            URL url = new URL(WIKIDATA_API);

            connection =
                    (HttpURLConnection) url.openConnection();

            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty(
                    "User-Agent",
                    "PS3OnlineConnect/4.0"
            );

            int code = connection.getResponseCode();

            if (code >= 200 && code < 300) {

                return new Item(
                        "🔎 API / Búsqueda",
                        "Wikidata",
                        State.OK,
                        "API respondió HTTP " + code
                );
            }

            return new Item(
                    "🔎 API / Búsqueda",
                    "Wikidata",
                    State.DEGRADED,
                    "HTTP " + code
            );

        } catch (Exception e) {

            return new Item(
                    "🔎 API / Búsqueda",
                    "Wikidata",
                    State.ERROR,
                    "No responde"
            );

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Item checkSignalingHttp() {

        HttpURLConnection connection = null;

        try {

            URL url = new URL(SIGNALING_HTTP);

            connection =
                    (HttpURLConnection) url.openConnection();

            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestMethod("GET");

            int code = connection.getResponseCode();

            if (code >= 200 && code < 500) {

                return new Item(
                        "☁️ Servidor de señalización",
                        "Render",
                        State.OK,
                        "Servidor accesible"
                );
            }

            return new Item(
                    "☁️ Servidor de señalización",
                    "Render",
                    State.DEGRADED,
                    "HTTP " + code
            );

        } catch (Exception e) {

            return new Item(
                    "☁️ Servidor de señalización",
                    "Render",
                    State.ERROR,
                    "No accesible"
            );

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Item checkWebSocket() {

        final Object lock = new Object();
        final boolean[] success = {false};
        final boolean[] finished = {false};

        try {

            Request request =
                    new Request.Builder()
                            .url(SIGNALING_WS)
                            .build();

            WebSocket socket =
                    httpClient.newWebSocket(
                            request,
                            new WebSocketListener() {

                                @Override
                                public void onOpen(
                                        WebSocket webSocket,
                                        Response response
                                ) {

                                    success[0] = true;
                                    finished[0] = true;

                                    webSocket.close(
                                            1000,
                                            "system-status"
                                    );

                                    synchronized (lock) {
                                        lock.notifyAll();
                                    }
                                }

                                @Override
                                public void onFailure(
                                        WebSocket webSocket,
                                        Throwable t,
                                        Response response
                                ) {

                                    finished[0] = true;

                                    synchronized (lock) {
                                        lock.notifyAll();
                                    }
                                }
                            }
                    );

            synchronized (lock) {

                if (!finished[0]) {
                    lock.wait(7000);
                }
            }

            socket.cancel();

            if (success[0]) {

                return new Item(
                        "🔌 WebSocket / Señalización",
                        "wss",
                        State.OK,
                        "Handshake correcto"
                );
            }

            return new Item(
                    "🔌 WebSocket / Señalización",
                    "wss",
                    State.ERROR,
                    "No se pudo establecer conexión"
            );

        } catch (Exception e) {

            return new Item(
                    "🔌 WebSocket / Señalización",
                    "wss",
                    State.ERROR,
                    "Error de conexión"
            );
        }
    }

    private Item checkStun() {

        DatagramSocket socket = null;

        try {

            StunP2P.Result result =
                    StunP2P.discover(
                            STUN_HOST,
                            STUN_PORT,
                            5000
                    );

            if (result != null && result.ok) {

                String endpoint =
                        result.mapped == null
                                ? "endpoint detectado"
                                : result.mapped.toString();

                if (result.socket != null) {
                    result.socket.close();
                }

                return new Item(
                        "📡 Servidor STUN",
                        STUN_HOST,
                        State.OK,
                        endpoint
                );
            }

            if (result != null &&
                    result.socket != null) {

                result.socket.close();
            }

            return new Item(
                    "📡 Servidor STUN",
                    STUN_HOST,
                    State.DEGRADED,
                    "Sin endpoint público confirmado"
            );

        } catch (Exception e) {

            if (socket != null) {
                socket.close();
            }

            return new Item(
                    "📡 Servidor STUN",
                    STUN_HOST,
                    State.ERROR,
                    "No respondió"
            );
        }
    }

    private Item checkUpnp() {

        DatagramSocket socket = null;

        try {

            socket = new DatagramSocket();
            socket.setSoTimeout(3000);

            String message =
                    "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 1\r\n" +
                    "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n" +
                    "\r\n";

            byte[] data =
                    message.getBytes(StandardCharsets.UTF_8);

            DatagramPacket request =
                    new DatagramPacket(
                            data,
                            data.length,
                            InetAddress.getByName(
                                    "239.255.255.250"
                            ),
                            1900
                    );

            socket.send(request);

            byte[] buffer = new byte[4096];

            DatagramPacket response =
                    new DatagramPacket(
                            buffer,
                            buffer.length
                    );

            socket.receive(response);

            String text =
                    new String(
                            response.getData(),
                            0,
                            response.getLength(),
                            StandardCharsets.UTF_8
                    );

            socket.close();

            if (text.toLowerCase().contains("location:")) {

                return new Item(
                        "🏠 UPnP / IGD",
                        "Router",
                        State.OK,
                        "Gateway UPnP detectado"
                );
            }

            return new Item(
                    "🏠 UPnP / IGD",
                    "Router",
                    State.DEGRADED,
                    "Respuesta sin ubicación"
            );

        } catch (Exception e) {

            if (socket != null) {
                socket.close();
            }

            return new Item(
                    "🏠 UPnP / IGD",
                    "Router",
                    State.UNKNOWN,
                    "No se pudo confirmar"
            );
        }
    }
}
