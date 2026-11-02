package dev.smallwright.agent;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The readable text of a web page: HTML reduced to its headings, paragraphs, list items, links with
 * their address and code blocks, without scripts, styles, navigation or footers; plain text,
 * Markdown, JSON and XML as they are.
 */
final class PageText {
    private static final Set<String> RAW = Set.of("script", "style", "textarea", "title");
    private static final Set<String> DROPPED =
            Set.of(
                    "script",
                    "style",
                    "textarea",
                    "title",
                    "noscript",
                    "nav",
                    "footer",
                    "svg",
                    "template",
                    "iframe",
                    "button",
                    "select");
    private static final Map<String, Integer> BLOCKS =
            Map.ofEntries(
                    Map.entry("p", 2),
                    Map.entry("ul", 2),
                    Map.entry("ol", 2),
                    Map.entry("dl", 2),
                    Map.entry("table", 2),
                    Map.entry("blockquote", 2),
                    Map.entry("section", 2),
                    Map.entry("article", 2),
                    Map.entry("main", 2),
                    Map.entry("header", 2),
                    Map.entry("aside", 2),
                    Map.entry("figure", 2),
                    Map.entry("details", 2),
                    Map.entry("div", 1),
                    Map.entry("tr", 1),
                    Map.entry("dt", 1),
                    Map.entry("dd", 1),
                    Map.entry("br", 1),
                    Map.entry("hr", 1),
                    Map.entry("summary", 1),
                    Map.entry("figcaption", 1));
    private static final Map<String, String> ENTITIES =
            Map.ofEntries(
                    Map.entry("amp", "&"),
                    Map.entry("lt", "<"),
                    Map.entry("gt", ">"),
                    Map.entry("quot", "\""),
                    Map.entry("apos", "'"),
                    Map.entry("nbsp", " "),
                    Map.entry("mdash", "—"),
                    Map.entry("ndash", "–"),
                    Map.entry("hellip", "…"),
                    Map.entry("copy", "©"),
                    Map.entry("reg", "®"),
                    Map.entry("trade", "™"),
                    Map.entry("lsquo", "‘"),
                    Map.entry("rsquo", "’"),
                    Map.entry("ldquo", "“"),
                    Map.entry("rdquo", "”"),
                    Map.entry("bull", "•"),
                    Map.entry("middot", "·"),
                    Map.entry("times", "×"),
                    Map.entry("rarr", "→"));

    private final String html;
    private final URI page;
    private final StringBuilder out = new StringBuilder();
    private int newlines;
    private boolean space;
    private boolean inPre;
    private boolean preStart;
    private String dropped;
    private int droppedDepth;
    private int droppedFrom;
    private final Set<String> unclosed = new HashSet<>();
    private String link;
    private int linkStart;

    private PageText(String html, URI page) {
        this.html = html;
        this.page = page;
    }

    /** The page's readable text, or null when its type is not text. */
    static String of(String contentType, String body, String url) {
        String type =
                contentType == null
                        ? ""
                        : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (type.equals("text/html") || type.equals("application/xhtml+xml")) {
            PageText text = new PageText(body, URI.create(url));
            text.read();
            return text.out.toString();
        }
        boolean readable =
                type.isEmpty()
                        || type.startsWith("text/")
                        || type.equals("application/json")
                        || type.equals("application/xml")
                        || type.equals("application/javascript")
                        || type.endsWith("+json")
                        || type.endsWith("+xml");
        return readable ? body : null;
    }

    /**
     * Reads the page. An element skipped to the end of the page was never closed, so the page is
     * read again from just after it with that kind of element kept.
     */
    private void read() {
        int at = 0;
        while (true) {
            while (at < html.length()) {
                int open = html.indexOf('<', at);
                if (open < 0) open = html.length();
                if (open > at) text(html.substring(at, open));
                if (open == html.length()) break;
                at = tag(open);
            }
            if (dropped == null) return;
            unclosed.add(dropped);
            dropped = null;
            at = droppedFrom;
        }
    }

    /** Reads the markup at {@code open}, a '<', and returns where the text after it starts. */
    private int tag(int open) {
        if (html.startsWith("<!--", open)) return after(html.indexOf("-->", open + 4), 3);
        char next = open + 1 < html.length() ? html.charAt(open + 1) : ' ';
        if (next == '!' || next == '?') return after(html.indexOf('>', open), 1);
        boolean closing = next == '/';
        int nameStart = closing ? open + 2 : open + 1;
        int nameEnd = nameStart;
        while (nameEnd < html.length() && Character.isLetterOrDigit(html.charAt(nameEnd)))
            nameEnd++;
        if (nameEnd == nameStart || !Character.isLetter(html.charAt(nameStart))) {
            text("<");
            return open + 1;
        }
        String name = html.substring(nameStart, nameEnd).toLowerCase(Locale.ROOT);
        int end = tagEnd(nameEnd);
        if (end < 0) return html.length();
        if (closing) closed(name);
        else {
            if (RAW.contains(name)) {
                int close = closingTag(name, end + 1);
                return close < 0 ? html.length() : after(html.indexOf('>', close), 1);
            }
            String attributes = html.substring(nameEnd, end);
            opened(name, attributes, attributes.stripTrailing().endsWith("/"), end + 1);
        }
        return end + 1;
    }

    private int closingTag(String name, int from) {
        String closing = "</" + name;
        for (int index = html.indexOf("</", from);
                index >= 0;
                index = html.indexOf("</", index + 2))
            if (html.regionMatches(true, index, closing, 0, closing.length())) return index;
        return -1;
    }

    private int after(int found, int length) {
        return found < 0 ? html.length() : found + length;
    }

    private int tagEnd(int from) {
        char quote = 0;
        for (int index = from; index < html.length(); index++) {
            char c = html.charAt(index);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == '>') return index;
        }
        return -1;
    }

    private void opened(String name, String attributes, boolean selfClosed, int after) {
        if (dropped != null) {
            if (name.equals(dropped) && !selfClosed) droppedDepth++;
            return;
        }
        if (DROPPED.contains(name) && !unclosed.contains(name)) {
            if (selfClosed) return;
            dropped = name;
            droppedDepth = 1;
            droppedFrom = after;
            return;
        }
        if (inPre) return;
        if (name.length() == 2
                && name.charAt(0) == 'h'
                && name.charAt(1) >= '1'
                && name.charAt(1) <= '6') {
            block(2);
            put("#".repeat(name.charAt(1) - '0') + " ");
        } else if (name.equals("li")) {
            block(1);
            put("- ");
        } else if (name.equals("pre")) {
            block(2);
            put("```\n");
            inPre = true;
            preStart = true;
        } else if (name.equals("code")) {
            put("`");
        } else if (name.equals("a")) {
            link = href(attributes);
            linkStart = out.length();
        } else if (BLOCKS.containsKey(name)) block(BLOCKS.get(name));
    }

    private void closed(String name) {
        if (dropped != null) {
            if (name.equals(dropped) && --droppedDepth == 0) dropped = null;
            return;
        }
        if (inPre) {
            if (!name.equals("pre")) return;
            inPre = false;
            if (out.charAt(out.length() - 1) != '\n') out.append('\n');
            out.append("```");
            block(2);
        } else if (name.length() == 2 && name.charAt(0) == 'h' && Character.isDigit(name.charAt(1)))
            block(2);
        else if (name.equals("code")) out.append('`');
        else if (name.equals("a") && link != null) {
            if (!out.substring(linkStart).trim().equals(link))
                out.append(" (").append(link).append(')');
            link = null;
        } else if (name.equals("li")) block(1);
        else if (BLOCKS.containsKey(name)) block(BLOCKS.get(name));
    }

    private String href(String attributes) {
        int at = 0;
        while (at < attributes.length()) {
            while (at < attributes.length() && !Character.isLetter(attributes.charAt(at))) at++;
            int nameEnd = at;
            while (nameEnd < attributes.length()
                    && "= \t\r\n/>".indexOf(attributes.charAt(nameEnd)) < 0) nameEnd++;
            String name = attributes.substring(at, nameEnd).toLowerCase(Locale.ROOT);
            at = nameEnd;
            while (at < attributes.length() && Character.isWhitespace(attributes.charAt(at))) at++;
            if (at >= attributes.length() || attributes.charAt(at) != '=') continue;
            at++;
            while (at < attributes.length() && Character.isWhitespace(attributes.charAt(at))) at++;
            if (at >= attributes.length()) return null;
            char quote = attributes.charAt(at);
            int valueEnd;
            if (quote == '"' || quote == '\'') {
                valueEnd = attributes.indexOf(quote, ++at);
                if (valueEnd < 0) valueEnd = attributes.length();
            } else {
                valueEnd = at;
                while (valueEnd < attributes.length()
                        && !Character.isWhitespace(attributes.charAt(valueEnd))) valueEnd++;
            }
            String value = decode(attributes.substring(at, valueEnd)).trim();
            at = valueEnd + 1;
            if (name.equals("href")) return address(value);
        }
        return null;
    }

    private String address(String href) {
        if (href.isEmpty() || href.startsWith("#")) return null;
        try {
            URI resolved = page.resolve(href);
            String scheme = resolved.getScheme();
            return scheme != null && (scheme.equals("https") || scheme.equals("http"))
                    ? resolved.toString()
                    : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private void text(String raw) {
        if (dropped != null) return;
        String text = decode(raw);
        if (inPre) {
            if (preStart && text.startsWith("\n")) text = text.substring(1);
            preStart = false;
            out.append(text);
            return;
        }
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (Character.isWhitespace(c) || c == ' ') space = true;
            else {
                flush();
                out.append(c);
            }
        }
    }

    private void put(String marker) {
        flush();
        out.append(marker);
    }

    private void block(int lines) {
        newlines = Math.max(newlines, lines);
        space = false;
    }

    private void flush() {
        int length = out.length();
        if (length > 0 && newlines > 0) out.append(newlines == 1 ? "\n" : "\n\n");
        else if (length > 0 && space && !Character.isWhitespace(out.charAt(length - 1)))
            out.append(' ');
        newlines = 0;
        space = false;
    }

    private static String decode(String text) {
        if (text.indexOf('&') < 0) return text;
        StringBuilder decoded = new StringBuilder(text.length());
        int at = 0;
        while (at < text.length()) {
            char c = text.charAt(at);
            int end = c == '&' ? text.indexOf(';', at) : -1;
            String value =
                    end > at + 1 && end - at <= 10 ? entity(text.substring(at + 1, end)) : null;
            if (value == null) {
                decoded.append(c);
                at++;
            } else {
                decoded.append(value);
                at = end + 1;
            }
        }
        return decoded.toString();
    }

    private static String entity(String name) {
        if (!name.startsWith("#")) return ENTITIES.get(name);
        try {
            int code =
                    name.length() > 1 && (name.charAt(1) == 'x' || name.charAt(1) == 'X')
                            ? Integer.parseInt(name.substring(2), 16)
                            : Integer.parseInt(name.substring(1));
            return Character.isValidCodePoint(code) && code != 0
                    ? new String(Character.toChars(code))
                    : null;
        } catch (NumberFormatException malformed) {
            return null;
        }
    }
}
