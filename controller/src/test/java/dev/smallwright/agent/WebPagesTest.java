package dev.smallwright.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import dev.smallwright.net.PublicAddress;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** One web page read over a fake web: no real site is ever asked. */
public final class WebPagesTest {
    private static final String PUBLIC = "93.184.216.34";

    private final Map<String, Answer> answers = new HashMap<>();
    private final Map<String, String> addresses = new HashMap<>();
    final List<FakeConnection> opened = Collections.synchronizedList(new ArrayList<>());
    private final WebPages web = new WebPages(this::resolve, url -> open(url));

    private static final class Answer {
        final int status;
        final Map<String, String> headers = new HashMap<>();
        final InputStream body;

        Answer(int status, InputStream body) {
            this.status = status;
            this.body = body;
        }
    }

    private InetAddress[] resolve(String host) throws UnknownHostException {
        String address = addresses.getOrDefault(host, PUBLIC);
        if (address.isEmpty()) throw new UnknownHostException(host);
        return new InetAddress[] {InetAddress.getByName(address)};
    }

    private HttpURLConnection open(URL url) {
        FakeConnection connection = new FakeConnection(url, answers.get(url.toString()));
        opened.add(connection);
        return connection;
    }

    private Answer page(String url, String type, String body) {
        Answer answer =
                new Answer(200, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        if (type != null) answer.headers.put("Content-Type", type);
        answers.put(url, answer);
        return answer;
    }

    private void redirect(String from, int status, String location) {
        Answer answer = new Answer(status, new ByteArrayInputStream(new byte[0]));
        answer.headers.put("Location", location);
        answers.put(from, answer);
    }

    private WebPages.Page read(String url) throws IOException {
        return web.read(url, new ModelClient.Cancellation());
    }

    private String refusal(String url) {
        return assertThrows(IOException.class, () -> read(url)).getMessage();
    }

    @Test
    public void readsAPageAsTextWithAPlainUserAgentAndNoCookiesOrCredentials() throws Exception {
        page(
                "https://developer.android.com/guide",
                "text/html; charset=utf-8",
                "<h1>Guide</h1><p>Hello é</p>");
        WebPages.Page page = read("https://developer.android.com/guide#part");
        assertEquals("https://developer.android.com/guide", page.url);
        assertEquals("# Guide\n\nHello é", page.text);
        assertEquals(1, opened.size());
        FakeConnection sent = opened.get(0);
        assertEquals("GET", sent.getRequestMethod());
        assertEquals("smallwright", sent.headers.get("User-Agent"));
        assertNull(sent.headers.get("Authorization"));
        assertNull(sent.headers.get("Cookie"));
        assertEquals(false, sent.getInstanceFollowRedirects());
        assertEquals(false, sent.getUseCaches());
        assertTrue(sent.disconnected);
    }

    @Test
    public void readsTheCharsetThePageNames() throws Exception {
        Answer latin = new Answer(200, new ByteArrayInputStream(new byte[] {(byte) 0xE9}));
        latin.headers.put("Content-Type", "text/plain; charset=ISO-8859-1");
        answers.put("https://example.com/a.txt", latin);
        assertEquals("\u00e9", read("https://example.com/a.txt").text);
    }

    @Test
    public void refusesEveryAddressThatIsNotAnHttpsWebAddress() {
        for (String url :
                List.of(
                        "http://example.com/",
                        "ftp://example.com/",
                        "file:///etc/hosts",
                        "content://media/1",
                        "example.com/page",
                        "https:///nohost",
                        "https://user:secret@example.com/",
                        "not an address at all")) refusal(url);
        assertTrue(opened.isEmpty());
    }

    @Test
    public void refusesAnAddressHoldingWhatLooksLikeAKey() {
        String message =
                refusal("https://example.com/collect?api_key=sk-or-v1-abcdefghijklmnopqrstu");
        assertTrue(message, message.contains("key or password"));
        assertTrue(message, !message.contains("sk-or-v1"));
        assertTrue(opened.isEmpty());
    }

    @Test
    public void refusesHostsOnLoopbackPrivateOrLinkLocalAddressesBeforeConnecting() {
        addresses.put("intranet.example", "10.1.2.3");
        addresses.put("metadata.example", "169.254.169.254");
        addresses.put("mixed.example", "192.168.0.10");
        for (String url :
                List.of(
                        "https://intranet.example/",
                        "https://metadata.example/latest",
                        "https://mixed.example/",
                        "https://127.0.0.1/",
                        "https://[::1]:8443/",
                        "https://[fd00::1]/"))
            assertEquals(url, PublicAddress.NOT_PUBLIC, refusal(url));
        assertTrue(opened.isEmpty());
    }

    @Test
    public void refusesAHostThatHasNoAddress() {
        addresses.put("nowhere.example", "");
        assertEquals(
                "There is no website at nowhere.example.", refusal("https://nowhere.example/"));
        assertTrue(opened.isEmpty());
    }

    @Test
    public void followsRedirectsToPublicHttpsPagesIncludingRelativeOnes() throws Exception {
        redirect("https://example.com/old", 301, "https://docs.example.com/start");
        redirect("https://docs.example.com/start", 302, "/next");
        redirect("https://docs.example.com/next", 307, "final?x=1");
        page("https://docs.example.com/final?x=1", "text/plain", "arrived");
        WebPages.Page page = read("https://example.com/old");
        assertEquals("arrived", page.text);
        assertEquals("https://docs.example.com/final?x=1", page.url);
        assertEquals(4, opened.size());
    }

    @Test
    public void refusesARedirectToAPrivateHostOrToPlainHttpWithoutOpeningIt() {
        addresses.put("intranet.example", "10.0.0.7");
        redirect("https://example.com/a", 302, "https://intranet.example/admin");
        assertEquals(PublicAddress.NOT_PUBLIC, refusal("https://example.com/a"));
        redirect("https://example.com/b", 308, "https://[::ffff:127.0.0.1]/");
        assertEquals(PublicAddress.NOT_PUBLIC, refusal("https://example.com/b"));
        redirect("https://example.com/c", 303, "http://example.com/c");
        refusal("https://example.com/c");
        assertEquals(3, opened.size());
    }

    @Test
    public void followsAtMostFiveRedirects() throws Exception {
        for (int hop = 0; hop < 6; hop++)
            redirect("https://example.com/" + hop, 302, "https://example.com/" + (hop + 1));
        page("https://example.com/6", "text/plain", "too far");
        page("https://example.com/5b", "text/plain", "in reach");
        String message = refusal("https://example.com/0");
        assertTrue(message, message.contains("redirect"));
        assertEquals(WebPages.MAX_REDIRECTS + 1, opened.size());
        opened.clear();
        redirect("https://example.com/4", 302, "https://example.com/5b");
        assertEquals("in reach", read("https://example.com/0").text);
    }

    @Test
    public void refusesAPageLargerThanTheLimitWhetherOrNotItSaysSoUpFront() {
        Answer declared = page("https://example.com/big", "text/plain", "small");
        declared.headers.put("Content-Length", String.valueOf(WebPages.MAX_BYTES + 1));
        assertTrue(refusal("https://example.com/big").contains("larger than"));
        answers.put(
                "https://example.com/endless",
                new Answer(
                        200,
                        new InputStream() {
                            @Override
                            public int read() {
                                return 'a';
                            }
                        }));
        assertTrue(refusal("https://example.com/endless").contains("larger than"));
    }

    @Test
    public void reportsErrorsAndPagesThatAreNotText() {
        answers.put(
                "https://example.com/gone", new Answer(404, new ByteArrayInputStream(new byte[0])));
        assertEquals("example.com answered HTTP 404.", refusal("https://example.com/gone"));
        page("https://example.com/logo.png", "image/png", "png");
        assertEquals("That page is not text (image/png).", refusal("https://example.com/logo.png"));
    }

    @Test
    public void stopEndsAReadThatIsWaitingForThePage() throws Exception {
        CountDownLatch waiting = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        answers.put(
                "https://example.com/slow",
                new Answer(
                        200,
                        new InputStream() {
                            @Override
                            public int read() throws IOException {
                                waiting.countDown();
                                try {
                                    released.await();
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                }
                                throw new IOException("Socket closed");
                            }
                        }));
        ModelClient.Cancellation stop = new ModelClient.Cancellation();
        IOException[] failure = new IOException[1];
        Thread reader =
                new Thread(
                        () -> {
                            try {
                                web.read("https://example.com/slow", stop);
                            } catch (IOException ended) {
                                failure[0] = ended;
                            }
                        });
        reader.start();
        assertTrue(waiting.await(5, TimeUnit.SECONDS));
        FakeConnection connection = opened.get(0);
        connection.onDisconnect = released::countDown;
        stop.cancel();
        reader.join(5_000);
        assertTrue(!reader.isAlive());
        assertTrue(connection.disconnected);
        assertTrue(failure[0] != null);
        assertTrue(connection.readTimeout() > 0);
    }

    @Test
    public void aCancelledReadOpensNothing() {
        ModelClient.Cancellation stop = new ModelClient.Cancellation();
        stop.cancel();
        page("https://example.com/", "text/plain", "x");
        assertThrows(IOException.class, () -> web.read("https://example.com/", stop));
        assertTrue(opened.isEmpty());
    }

    @Test
    public void refusesAConnectionThatReachedALoopbackAddressAfterTheLookupSaidPublic()
            throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("localhost"))) {
            server.setSoTimeout(5_000);
            WebPages rebound =
                    new WebPages(
                            host -> new InetAddress[] {InetAddress.getByName(PUBLIC)},
                            WebPages::guarded);
            IOException[] failure = new IOException[1];
            Thread reader =
                    new Thread(
                            () -> {
                                try {
                                    rebound.read(
                                            "https://localhost:" + server.getLocalPort() + "/",
                                            new ModelClient.Cancellation());
                                } catch (IOException refused) {
                                    failure[0] = refused;
                                }
                            });
            reader.start();
            try (Socket accepted = server.accept()) {
                accepted.setSoTimeout(5_000);
                assertEquals(-1, accepted.getInputStream().read());
            }
            reader.join(10_000);
            assertEquals(PublicAddress.NOT_PUBLIC, failure[0].getMessage());
        }
    }

    final class FakeConnection extends HttpURLConnection {
        private final Answer answer;
        final Map<String, String> headers = new HashMap<>();
        volatile boolean disconnected;
        volatile Runnable onDisconnect = () -> {};

        FakeConnection(URL url, Answer answer) {
            super(url);
            this.answer = answer;
        }

        int readTimeout() {
            return getReadTimeout();
        }

        @Override
        public void setRequestProperty(String key, String value) {
            headers.put(key, value);
        }

        @Override
        public void connect() {}

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void disconnect() {
            disconnected = true;
            onDisconnect.run();
        }

        @Override
        public int getResponseCode() throws IOException {
            if (answer == null) return 404;
            return answer.status;
        }

        @Override
        public String getHeaderField(String name) {
            return answer == null ? null : answer.headers.get(name);
        }

        @Override
        public String getContentType() {
            return getHeaderField("Content-Type");
        }

        @Override
        public long getContentLengthLong() {
            String length = getHeaderField("Content-Length");
            return length == null ? -1 : Long.parseLong(length);
        }

        @Override
        public InputStream getInputStream() {
            return answer.body;
        }
    }
}
