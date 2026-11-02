package dev.smallwright.agent;

import dev.smallwright.net.PublicAddress;
import dev.smallwright.security.ContextFilter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;

/**
 * Reads one public web page for the builder: an HTTPS GET with no cookies or credentials, to hosts
 * whose every address is public, checked again on the socket each connection really made, through
 * at most {@link #MAX_REDIRECTS} redirects. Stop ends it; a page that stays silent for {@link
 * #STALL_MILLIS} counts as gone.
 */
public final class WebPages {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_REDIRECTS = 5;
    static final int STALL_MILLIS = 30_000;
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    /** Opens a connection to one address. */
    public interface Opener {
        HttpURLConnection open(URL url) throws IOException;
    }

    /** The page's final address, after redirects, and its readable text. */
    public static final class Page {
        public final String url;
        public final String text;

        public Page(String url, String text) {
            this.url = url;
            this.text = text;
        }
    }

    public static final WebPages PUBLIC =
            new WebPages(InetAddress::getAllByName, WebPages::guarded);

    private final PublicAddress.Resolver resolver;
    private final Opener opener;

    WebPages(PublicAddress.Resolver resolver, Opener opener) {
        this.resolver = resolver;
        this.opener = opener;
    }

    /** A direct connection, never through a proxy, whose TLS opens only to a public address. */
    static HttpURLConnection guarded(URL url) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection(Proxy.NO_PROXY);
        connection.setSSLSocketFactory(PublicAddress.SOCKETS);
        return connection;
    }

    /** The page at {@code address}; the message of a refusal is plain and safe to show. */
    public Page read(String address, ModelClient.Cancellation cancellation) throws IOException {
        URL url = checked(address);
        for (int redirects = 0; ; redirects++) {
            cancellation.check();
            PublicAddress.resolvePublic(url.getHost(), resolver);
            HttpURLConnection connection = opener.open(url);
            try {
                cancellation.watch(connection);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(STALL_MILLIS);
                connection.setRequestProperty("User-Agent", "smallwright");
                connection.setRequestProperty(
                        "Accept",
                        "text/html, text/markdown, text/plain, application/json;q=0.9, */*;q=0.5");
                int status = connection.getResponseCode();
                cancellation.check();
                if (REDIRECTS.contains(status)) {
                    if (redirects == MAX_REDIRECTS)
                        throw new Refusal(
                                "That page sent the builder through more than "
                                        + MAX_REDIRECTS
                                        + " redirects, so it was not read.");
                    url = redirected(url, connection.getHeaderField("Location"));
                    continue;
                }
                if (status != 200)
                    throw new Refusal(url.getHost() + " answered HTTP " + status + ".");
                String type = connection.getContentType();
                if (connection.getContentLengthLong() > MAX_BYTES) throw tooLarge();
                byte[] body = body(connection, cancellation);
                String text = PageText.of(type, new String(body, charset(type)), url.toString());
                if (text == null)
                    throw new Refusal(
                            "That page is not text ("
                                    + type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT)
                                    + ").");
                return new Page(url.toString(), text);
            } catch (IOException | RuntimeException failure) {
                cancellation.check();
                throw plain(failure, url);
            } finally {
                connection.disconnect();
            }
        }
    }

    private static byte[] body(HttpURLConnection connection, ModelClient.Cancellation cancellation)
            throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (InputStream input = connection.getInputStream()) {
            byte[] buffer = new byte[16 * 1024];
            for (int read; (read = input.read(buffer)) != -1; ) {
                cancellation.check();
                if (body.size() + read > MAX_BYTES) throw tooLarge();
                body.write(buffer, 0, read);
            }
        }
        return body.toByteArray();
    }

    private static final class Refusal extends IOException {
        Refusal(String message) {
            super(message);
        }
    }

    private static IOException tooLarge() {
        return new Refusal("That page is larger than 2 MB, so it was not read.");
    }

    private static IOException plain(Exception failure, URL url) {
        if (failure instanceof Refusal) return (Refusal) failure;
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (PublicAddress.NOT_PUBLIC.equals(cause.getMessage()))
                return new Refusal(PublicAddress.NOT_PUBLIC);
        if (failure instanceof SocketTimeoutException)
            return new Refusal(url.getHost() + " stopped answering.");
        return new Refusal(
                "Could not reach "
                        + url.getHost()
                        + ": the phone may be offline, or the site did not answer securely.");
    }

    private static Charset charset(String type) {
        if (type != null)
            for (String part : type.split(";")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && pair[0].trim().equalsIgnoreCase("charset"))
                    try {
                        return Charset.forName(pair[1].trim().replace("\"", ""));
                    } catch (RuntimeException unknown) {
                        return StandardCharsets.UTF_8;
                    }
            }
        return StandardCharsets.UTF_8;
    }

    private static URL redirected(URL from, String location) throws IOException {
        if (location == null || location.isBlank())
            throw new Refusal("That page sent a redirect without an address.");
        try {
            return checked(from.toURI().resolve(location.trim()).toString());
        } catch (URISyntaxException | IllegalArgumentException malformed) {
            throw new Refusal("That page sent a redirect to an address that is not valid.");
        }
    }

    /** {@code address} as an https URL without a fragment, or a refusal saying why not. */
    private static URL checked(String address) throws IOException {
        URI uri;
        try {
            uri = new URI(address.trim());
        } catch (URISyntaxException malformed) {
            throw new Refusal("That is not a web address.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()))
            throw new Refusal("Only https:// addresses can be read.");
        if (uri.getHost() == null || uri.getHost().isEmpty())
            throw new Refusal("That is not a web address.");
        if (uri.getRawUserInfo() != null)
            throw new Refusal("Addresses with a user name or password are not opened.");
        if (!ContextFilter.redact(address).equals(address))
            throw new Refusal(
                    "That address holds what looks like a key or password, so it was not opened.");
        String whole = address.trim();
        try {
            return new URL(
                    uri.getRawFragment() == null ? whole : whole.substring(0, whole.indexOf('#')));
        } catch (MalformedURLException bad) {
            throw new Refusal("That is not a web address.");
        }
    }
}
