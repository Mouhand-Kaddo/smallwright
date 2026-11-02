package dev.smallwright.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** A one-file HTTP server on 127.0.0.1 only, so downloads can be tested against real sockets. */
final class FixtureServer implements AutoCloseable {
    interface Handler {
        void serve(long rangeStart, OutputStream out) throws IOException;
    }

    private final ServerSocket socket;
    private volatile Handler handler;
    private volatile int requests;

    FixtureServer(Handler handler) throws IOException {
        this.handler = handler;
        socket = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        Thread thread = new Thread(this::acceptLoop, "fixture-server");
        thread.setDaemon(true);
        thread.start();
    }

    String url() {
        return "http://127.0.0.1:" + socket.getLocalPort() + "/platform.zip";
    }

    int requests() {
        return requests;
    }

    void handler(Handler next) {
        handler = next;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    static void send(OutputStream out, int status, String extraHeader, long declared, byte[] body)
            throws IOException {
        String head =
                "HTTP/1.1 "
                        + status
                        + " X\r\n"
                        + (declared < 0 ? "" : "Content-Length: " + declared + "\r\n")
                        + extraHeader
                        + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    static void sendAll(OutputStream out, byte[] body) throws IOException {
        send(out, 200, "", body.length, body);
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try (Socket client = socket.accept()) {
                requests++;
                BufferedReader in =
                        new BufferedReader(
                                new InputStreamReader(
                                        client.getInputStream(), StandardCharsets.US_ASCII));
                long start = 0;
                for (String line; (line = in.readLine()) != null && !line.isEmpty(); ) {
                    if (line.toLowerCase().startsWith("range: bytes="))
                        start = Long.parseLong(line.substring(13, line.indexOf('-')));
                }
                try {
                    handler.serve(start, client.getOutputStream());
                } catch (IOException clientGone) {
                    continue;
                }
            } catch (IOException closed) {
                return;
            }
        }
    }
}
