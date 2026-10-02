package com.ps3online.dnstester.stun;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;

public class StunP2P {

    private static final int BINDING_SUCCESS = 0x0101;
    private static final int MAGIC_COOKIE = 0x2112A442;
    private static final int XOR_MAPPED_ADDRESS = 0x0020;

    public static class Result {
        public boolean ok;
        public String message;
        public InetSocketAddress mapped;
        public DatagramSocket socket;

        public Result(boolean ok, String message,
                      InetSocketAddress mapped,
                      DatagramSocket socket) {
            this.ok = ok;
            this.message = message;
            this.mapped = mapped;
            this.socket = socket;
        }
    }

    public static Result discover(String host, int port, int timeout)
            throws Exception {

        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(timeout);

        byte[] transactionId = new byte[12];
        new Random().nextBytes(transactionId);

        byte[] request = new byte[20];

        request[0] = 0x00;
        request[1] = 0x01;

        request[2] = 0x00;
        request[3] = 0x00;

        request[4] = 0x21;
        request[5] = 0x12;
        request[6] = (byte) 0xA4;
        request[7] = 0x42;

        System.arraycopy(transactionId, 0, request, 8, 12);

        InetAddress server = InetAddress.getByName(host);

        socket.send(new DatagramPacket(
                request,
                request.length,
                server,
                port
        ));

        byte[] buffer = new byte[2048];

        DatagramPacket response =
                new DatagramPacket(buffer, buffer.length);

        socket.receive(response);

        InetSocketAddress mapped =
                parse(buffer, response.getLength(), transactionId);

        if (mapped == null) {
            socket.close();

            return new Result(
                    false,
                    "respuesta STUN inválida o no correspondiente a la solicitud",
                    null,
                    null
            );
        }

        return new Result(
                true,
                "endpoint público "
                        + mapped.getAddress().getHostAddress()
                        + ":"
                        + mapped.getPort(),
                mapped,
                socket
        );
    }

    static InetSocketAddress parse(
            byte[] data,
            int length,
            byte[] expectedTransactionId) {

        try {
            if (data == null ||
                    length < 20 ||
                    expectedTransactionId == null ||
                    expectedTransactionId.length != 12) {
                return null;
            }

            ByteBuffer header =
                    ByteBuffer.wrap(data, 0, length);

            int messageType =
                    header.getShort() & 0xFFFF;

            int messageLength =
                    header.getShort() & 0xFFFF;

            int magicCookie =
                    header.getInt();

            if (messageType != BINDING_SUCCESS) {
                return null;
            }

            if (magicCookie != MAGIC_COOKIE) {
                return null;
            }

            if (messageLength > length - 20) {
                return null;
            }

            for (int i = 0; i < 12; i++) {
                if (data[8 + i] != expectedTransactionId[i]) {
                    return null;
                }
            }

            int pos = 20;
            int end = 20 + messageLength;

            while (pos + 4 <= end &&
                    pos + 4 <= length) {

                int type =
                        ((data[pos] & 0xFF) << 8)
                                | (data[pos + 1] & 0xFF);

                int attributeLength =
                        ((data[pos + 2] & 0xFF) << 8)
                                | (data[pos + 3] & 0xFF);

                int valueStart = pos + 4;

                if (valueStart + attributeLength > end ||
                        valueStart + attributeLength > length) {
                    return null;
                }

                if (type == XOR_MAPPED_ADDRESS &&
                        attributeLength >= 8) {

                    int family =
                            data[valueStart + 1] & 0xFF;

                    if (family != 0x01) {
                        return null;
                    }

                    int mappedPort =
                            ((data[valueStart + 2] & 0xFF) << 8)
                                    | (data[valueStart + 3] & 0xFF);

                    mappedPort ^= (MAGIC_COOKIE >>> 16);

                    byte[] address = new byte[4];

                    for (int i = 0; i < 4; i++) {
                        address[i] =
                                (byte) (
                                        (data[valueStart + 4 + i] & 0xFF)
                                                ^ ((MAGIC_COOKIE
                                                >>> (24 - (i * 8)))
                                                & 0xFF)
                                );
                    }

                    return new InetSocketAddress(
                            InetAddress.getByAddress(address),
                            mappedPort
                    );
                }

                int paddedLength =
                        (attributeLength + 3) & ~3;

                pos += 4 + paddedLength;
            }

        } catch (Exception ignored) {
        }

        return null;
    }
}
