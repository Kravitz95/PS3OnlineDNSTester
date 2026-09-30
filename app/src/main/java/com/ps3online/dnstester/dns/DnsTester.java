package com.ps3online.dnstester.dns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

public class DnsTester {

    public static final int DEFAULT_TIMEOUT_MS = 3000;

    public static class Result {
        public final boolean ok;
        public final long rttMs;
        public final String message;

        public Result(boolean ok, long rttMs, String message) {
            this.ok = ok;
            this.rttMs = rttMs;
            this.message = message;
        }
    }

    /**
     * Ejecuta una consulta DNS A real mediante UDP/53.
     */
    public static Result queryA(String resolver, String name, int timeoutMs) {
        long start = System.nanoTime();

        try (DatagramSocket socket = new DatagramSocket()) {

            socket.setSoTimeout(Math.min(timeoutMs, DEFAULT_TIMEOUT_MS));

            byte[] query = buildQuery(name);

            InetAddress server = InetAddress.getByName(resolver);

            DatagramPacket request =
                    new DatagramPacket(
                            query,
                            query.length,
                            server,
                            53
                    );

            socket.send(request);

            byte[] buffer = new byte[2048];

            DatagramPacket response =
                    new DatagramPacket(
                            buffer,
                            buffer.length
                    );

            socket.receive(response);

            long rtt = elapsedMs(start);

            if (response.getLength() < 12) {
                return new Result(
                        false,
                        rtt,
                        "Respuesta DNS inválida"
                );
            }

            int flags =
                    ((buffer[2] & 0xFF) << 8)
                            | (buffer[3] & 0xFF);

            int rcode = flags & 0x0F;

            if (rcode == 0) {
                return new Result(
                        true,
                        rtt,
                        "Respuesta DNS válida"
                );
            }

            return new Result(
                    false,
                    rtt,
                    "DNS RCODE=" + rcode
            );

        } catch (Exception e) {

            long rtt = elapsedMs(start);

            String message = e.getMessage();

            if (message == null || message.trim().isEmpty()) {
                message = e.getClass().getSimpleName();
            }

            return new Result(
                    false,
                    rtt,
                    message
            );
        }
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private static byte[] buildQuery(String name) throws IOException {

        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        Random random = new Random();

        int id = random.nextInt(65536);

        // Transaction ID
        out.write((id >> 8) & 0xFF);
        out.write(id & 0xFF);

        // Flags: standard recursive query
        out.write(0x01);
        out.write(0x00);

        // QDCOUNT = 1
        out.write(0x00);
        out.write(0x01);

        // ANCOUNT = 0
        out.write(0x00);
        out.write(0x00);

        // NSCOUNT = 0
        out.write(0x00);
        out.write(0x00);

        // ARCOUNT = 0
        out.write(0x00);
        out.write(0x00);

        for (String part : name.split("\\.")) {

            byte[] bytes = part.getBytes("UTF-8");

            out.write(bytes.length);
            out.write(bytes);
        }

        // End of domain name
        out.write(0x00);

        // QTYPE = A
        out.write(0x00);
        out.write(0x01);

        // QCLASS = IN
        out.write(0x00);
        out.write(0x01);

        return out.toByteArray();
    }
}
