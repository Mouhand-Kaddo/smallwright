package dev.smallwright.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * A stand-in for OpenRouter's image generation on 127.0.0.1: answers every chat completion with the
 * reply set last and records what was sent. No real image model is ever asked.
 */
public final class ImageServer implements AutoCloseable {
    private final ServerSocket server;
    private volatile int status = 200;
    private volatile byte[] reply = new byte[0];
    public final List<String> bodies = Collections.synchronizedList(new ArrayList<>());
    public final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());

    public ImageServer() throws IOException {
        server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        Thread acceptor =
                new Thread(
                        () -> {
                            while (!server.isClosed()) {
                                try (Socket client = server.accept()) {
                                    answer(client);
                                } catch (IOException closed) {
                                    if (server.isClosed()) return;
                                }
                            }
                        },
                        "image-server");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void answer(Socket client) throws IOException {
        DataInputStream in = new DataInputStream(client.getInputStream());
        int length = 0;
        String authorization = null;
        for (String line = line(in); !line.isEmpty(); line = line(in)) {
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("content-length:"))
                length = Integer.parseInt(line.substring(15).trim());
            if (lower.startsWith("authorization:")) authorization = line.substring(14).trim();
        }
        byte[] body = new byte[length];
        in.readFully(body);
        bodies.add(new String(body, StandardCharsets.UTF_8));
        authorizations.add(authorization);
        byte[] answer = reply;
        OutputStream out = client.getOutputStream();
        out.write(
                ("HTTP/1.1 "
                                + status
                                + " X\r\nContent-Type: application/json\r\nContent-Length: "
                                + answer.length
                                + "\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
        out.write(answer);
        out.flush();
    }

    private static String line(DataInputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int next = in.read(); next != -1 && next != '\n'; next = in.read())
            if (next != '\r') line.write(next);
        return line.toString(StandardCharsets.US_ASCII.name());
    }

    /** Connections that reach this server instead of OpenRouter's chat completions. */
    public ModelClient.ConnectionFactory connections() {
        return connections(ModelClient.ENDPOINT);
    }

    /** Connections that reach this server instead of {@code endpoint}. */
    public ModelClient.ConnectionFactory connections(String endpoint) {
        return url -> {
            if (!endpoint.equals(url.toString())) throw new AssertionError("Unexpected URL " + url);
            return (HttpURLConnection)
                    new URL(
                                    "http://127.0.0.1:"
                                            + server.getLocalPort()
                                            + "/api/v1/chat/completions")
                            .openConnection();
        };
    }

    public ImageClient client() {
        return new ImageClient(connections());
    }

    public ImageServer answer(int status, String body) {
        this.status = status;
        reply = body.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    /** A reply carrying this data URL as its one image, with the cost reported. */
    public ImageServer image(String dataUrl, String cost) {
        return answer(200, reply(ImageClient.MODEL, dataUrl, cost).toString());
    }

    public ImageServer png(byte[] png, String cost) {
        return image(dataUrl(png), cost);
    }

    public static String dataUrl(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    public static JsonObject reply(String model, String dataUrl, String cost) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", "Here is your icon.");
        if (dataUrl != null) {
            JsonObject url = new JsonObject();
            url.addProperty("url", dataUrl);
            JsonObject image = new JsonObject();
            image.addProperty("type", "image_url");
            image.add("image_url", url);
            JsonArray images = new JsonArray();
            images.add(image);
            message.add("images", images);
        }
        JsonObject choice = new JsonObject();
        choice.addProperty("finish_reason", "stop");
        choice.add("message", message);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        JsonObject usage = new JsonObject();
        usage.addProperty("prompt_tokens", 40);
        usage.addProperty("completion_tokens", 1290);
        usage.addProperty("total_tokens", 1330);
        usage.addProperty("cost", new java.math.BigDecimal(cost));
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.add("choices", choices);
        root.add("usage", usage);
        return root;
    }

    /** A real PNG of this size, one colour, written without any image library. */
    public static byte[] png(int width, int height) {
        byte[] row = new byte[1 + width * 4];
        for (int x = 0; x < width; x++) {
            row[1 + x * 4] = 0x22;
            row[2 + x * 4] = 0x66;
            row[3 + x * 4] = (byte) 0xaa;
            row[4 + x * 4] = (byte) 0xff;
        }
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (int y = 0; y < height; y++) raw.write(row, 0, row.length);
        Deflater deflater = new Deflater();
        deflater.setInput(raw.toByteArray());
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        while (!deflater.finished()) compressed.write(chunk, 0, deflater.deflate(chunk));
        deflater.end();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(SIGNATURE, 0, SIGNATURE.length);
        chunk(
                png,
                "IHDR",
                ByteBuffer.allocate(13)
                        .putInt(width)
                        .putInt(height)
                        .put(new byte[] {8, 6, 0, 0, 0})
                        .array());
        chunk(png, "IDAT", compressed.toByteArray());
        chunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        ByteBuffer bytes = ByteBuffer.allocate(12 + data.length);
        bytes.putInt(data.length).put(name).put(data).putInt((int) crc.getValue());
        out.write(bytes.array(), 0, bytes.capacity());
    }

    /** Only a PNG signature and header chunk claiming this size; no picture follows. */
    public static byte[] header(int width, int height) {
        ByteBuffer bytes = ByteBuffer.allocate(8 + 8 + 13 + 4);
        bytes.put(SIGNATURE);
        bytes.putInt(13).put("IHDR".getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(width).putInt(height).put((byte) 8).put((byte) 6).put(new byte[3]);
        bytes.putInt(0);
        return bytes.array();
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
