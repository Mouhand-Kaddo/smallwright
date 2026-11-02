package dev.smallwright.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * One bounded GET, as the controller makes every download: no redirects, short timeouts, a size cap
 * enforced while reading, and plain messages that name the source and the file.
 */
public final class HttpsDownload {
    /** The source answered 404 or 410. */
    public static final class NotFound extends IOException {}

    /** The source answered with a status other than 200, a redirect or not found. */
    public static final class Refused extends IOException {
        public final int code;

        Refused(String source, int code, String name) {
            super(source + " answered HTTP " + code + " for " + name);
            this.code = code;
        }
    }

    private HttpsDownload() {}

    /** The whole body of {@code url}, at most {@code maxBytes}. */
    public static byte[] bytes(
            String source, String url, String name, long maxBytes, String... headers)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        fetch(source, url, name, maxBytes, bytes, headers);
        return bytes.toByteArray();
    }

    /** Writes the body of {@code url} to {@code output}. Headers come as name, value pairs. */
    public static void fetch(
            String source,
            String url,
            String name,
            long maxBytes,
            OutputStream output,
            String... headers)
            throws IOException {
        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
        } catch (IOException | RuntimeException invalid) {
            throw new IOException("Could not reach " + source + " for " + name);
        }
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("User-Agent", "smallwright");
            for (int index = 0; index + 1 < headers.length; index += 2)
                connection.setRequestProperty(headers[index], headers[index + 1]);
            int code;
            try {
                code = connection.getResponseCode();
            } catch (IOException offline) {
                throw new IOException(
                        "Could not reach "
                                + source
                                + " for "
                                + name
                                + ": the phone may be offline");
            }
            if (code == 404 || code == 410) throw new NotFound();
            if (code >= 300 && code < 400)
                throw new IOException(
                        source
                                + " answered "
                                + name
                                + " with a redirect, which the builder does not follow");
            if (code != 200) throw new Refused(source, code, name);
            if (connection.getContentLengthLong() > maxBytes)
                throw new IOException(name + " is larger than the builder accepts");
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                for (int read; (read = input.read(buffer)) != -1; ) {
                    total += read;
                    if (total > maxBytes)
                        throw new IOException(name + " is larger than the builder accepts");
                    output.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}
