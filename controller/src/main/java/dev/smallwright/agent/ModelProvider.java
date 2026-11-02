package dev.smallwright.agent;

import dev.smallwright.net.PublicAddress;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Where a run's model requests go: OpenRouter, or one server the person named by its https base URL
 * that speaks OpenAI's chat completions API. Only OpenRouter gets provider routing, its reasoning
 * setting, web search, the image model and a reported cost (ADR 0009).
 */
public final class ModelProvider {
    public static final ModelProvider OPENROUTER =
            new ModelProvider("https://openrouter.ai/api/v1", "OpenRouter");

    static final int MAX_ADDRESS_CHARS = 300;

    /** The base URL requests add {@code /chat/completions} or {@code /models} to. */
    public final String baseUrl;

    /** How failures name it: "OpenRouter" or "Model server". */
    public final String label;

    private ModelProvider(String baseUrl, String label) {
        this.baseUrl = baseUrl;
        this.label = label;
    }

    /**
     * The server at a typed address: https only, with no name, password, query or fragment, in
     * printable ASCII, normalized to a lower-case host, no default port and no trailing slash.
     * Refusals say what to change and never repeat the address.
     */
    public static ModelProvider openAiCompatible(String typed) {
        String address = typed == null ? "" : typed.trim();
        if (address.isEmpty() || address.length() > MAX_ADDRESS_CHARS)
            throw new IllegalArgumentException(
                    "Type the server's address, such as https://api.openai.com/v1.");
        if (!address.chars().allMatch(c -> c > 0x20 && c < 0x7f))
            throw new IllegalArgumentException(
                    "The address has a space or a character it can't hold.");
        URI uri;
        try {
            uri = new URI(address).normalize();
        } catch (URISyntaxException malformed) {
            throw new IllegalArgumentException("That is not a web address.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()))
            throw new IllegalArgumentException("The address must start with https://.");
        if (uri.getRawUserInfo() != null)
            throw new IllegalArgumentException(
                    "Leave the name and password out of the address; the key goes in its own field.");
        if (uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("Leave out everything from ? or # on.");
        String host = uri.getHost();
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (host == null || host.isEmpty() || path.startsWith("/.."))
            throw new IllegalArgumentException("That is not a web address.");
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int port = uri.getPort();
        return new ModelProvider(
                "https://"
                        + host.toLowerCase(Locale.ROOT)
                        + (port == -1 || port == 443 ? "" : ":" + port)
                        + path,
                "Model server");
    }

    public boolean isOpenRouter() {
        return this == OPENROUTER;
    }

    /**
     * Opens a connection to {@code url}. A model server's connection opens TLS only to a public
     * internet address, so a typed address can't reach this phone or its local network.
     */
    java.net.HttpURLConnection open(java.net.URL url) throws IOException {
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        if (!isOpenRouter())
            ((javax.net.ssl.HttpsURLConnection) connection)
                    .setSSLSocketFactory(PublicAddress.SOCKETS);
        return connection;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModelProvider
                && ((ModelProvider) other).isOpenRouter() == isOpenRouter()
                && ((ModelProvider) other).baseUrl.equals(baseUrl);
    }

    @Override
    public int hashCode() {
        return baseUrl.hashCode() * 31 + (isOpenRouter() ? 1 : 0);
    }
}
