package dev.smallwright.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PageTextTest {
    private static final String PAGE = "https://developer.android.com/guide/topics/ui/";

    private static String html(String body) {
        return PageText.of("text/html; charset=utf-8", body, PAGE);
    }

    @Test
    public void keepsHeadingsParagraphsListItemsAndLinksWithTheirAddress() {
        String text =
                html(
                        "<!doctype html><html><head><title>Layouts</title>"
                                + "<meta charset=utf-8><link rel=stylesheet href=a.css></head>"
                                + "<body><h1>Layouts</h1><p>A layout defines the <b>structure"
                                + "</b>\n   of a   user interface.</p><h2 id=x>Kinds</h2><ul>"
                                + "<li>Linear</li><li>Constraint, see <a href=\"../constraint\">"
                                + "ConstraintLayout</a></li></ul><p>Read <a href='#top'>this"
                                + "</a> and <a href=\"https://example.com/x?a=1&amp;b=2\">that"
                                + "</a>.</p></body></html>");
        assertEquals(
                "# Layouts\n\n"
                        + "A layout defines the structure of a user interface.\n\n"
                        + "## Kinds\n\n"
                        + "- Linear\n"
                        + "- Constraint, see ConstraintLayout"
                        + " (https://developer.android.com/guide/topics/constraint)\n\n"
                        + "Read this and that (https://example.com/x?a=1&b=2).",
                text);
    }

    @Test
    public void keepsCodeBlocksWithTheirSpacingAndInlineCodeInBackticks() {
        String text =
                html(
                        "<p>Call <code>setContentView()</code>:</p><pre><code class=java>"
                                + "<span>if</span> (a &lt; b) {\n    run(&quot;x&quot;);\n}"
                                + "</code></pre><p>Done</p>");
        assertEquals(
                "Call `setContentView()`:\n\n```\nif (a < b) {\n    run(\"x\");\n}\n```\n\nDone",
                text);
    }

    @Test
    public void dropsScriptsStylesNavigationFootersFormsAndComments() {
        String text =
                html(
                        "<nav><ul><li>Home</li><li><nav>Inner</nav>Guides</li></ul></nav>"
                                + "<script>var s = '</nav><p>not text</p>';</script>"
                                + "<style>p { color: red }</style><!-- <p>hidden</p> -->"
                                + "<main><p>Kept &amp; shown&nbsp;here &#8212; &#x41;"
                                + " &unknown;</p></main><noscript>Turn on JS</noscript>"
                                + "<svg><text>icon</text></svg><footer>© 2026</footer>");
        assertEquals("Kept & shown here — A &unknown;", text);
    }

    @Test
    public void handlesUnclosedAndMalformedMarkupWithoutLosingText() {
        assertEquals("one\n\ntwo < three", html("<p>one<p>two &lt; three"));
        assertEquals("a < b", html("a < b"));
        assertEquals("tail", html("<script>never closed</script><p>tail"));
        assertEquals("", html("<script>never closed"));
    }

    @Test
    public void selfClosedSkippedElementsEndAtTheirOwnTag() {
        String text =
                html(
                        "<header><svg class=\"logo\" viewBox=\"0 0 16 16\"/></header>"
                                + "<iframe src=\"https://ads.example/\" />"
                                + "<template id=row/><main><h1>Room</h1>"
                                + "<p>Store data with <svg width=8 height=8 /> Room.</p>"
                                + "<svg><use href=\"#icon\"/></svg><p>Then query it.</p></main>"
                                + "<footer>© 2026</footer>");
        assertEquals("# Room\n\nStore data with Room.\n\nThen query it.", text);
    }

    @Test
    public void anUnclosedSkippedElementKeepsTheRestOfThePage() {
        assertEquals(
                "- Home\n- Guides\n\n# Room\n\nStore data with Room.",
                html(
                        "<nav class=top><ul><li>Home<li>Guides</ul>"
                                + "<h1>Room</h1><p>Store data with Room.</p>"));
        assertEquals("Docs\n\nKept.", html("<nav><nav>Docs</nav><p>Kept.</p>"));
        assertEquals("Kept.\n\nCopy", html("<nav>Menu</nav><p>Kept.</p><button>Copy"));
    }

    @Test(timeout = 10_000)
    public void manyUnclosedSkippedElementsStayQuick() {
        String text = html("<nav>x".repeat(100_000) + "</nav>" + "<button>y".repeat(100_000));
        assertTrue(text.startsWith("xxx"));
        assertTrue(text.endsWith("yyy"));
    }

    @Test
    public void returnsPlainTextMarkdownAndJsonAsTheyAre() {
        String markdown = "# Title\n\n<b>raw</b>   kept\n";
        assertEquals(markdown, PageText.of("text/markdown", markdown, PAGE));
        assertEquals(markdown, PageText.of("text/plain; charset=utf-8", markdown, PAGE));
        assertEquals("{\"a\": 1}", PageText.of("application/json", "{\"a\": 1}", PAGE));
        assertEquals("<x/>", PageText.of("application/vnd.api+json", "<x/>", PAGE));
        assertEquals("plain", PageText.of(null, "plain", PAGE));
    }

    @Test
    public void readsXhtmlAsHtmlAndRefusesTypesThatAreNotText() {
        assertEquals("Hi", PageText.of("application/xhtml+xml", "<p>Hi</p>", PAGE));
        assertNull(PageText.of("image/png", "x", PAGE));
        assertNull(PageText.of("application/pdf", "x", PAGE));
        assertNull(PageText.of("application/octet-stream", "x", PAGE));
    }

    @Test
    public void aLongPageStaysWhole() {
        StringBuilder page = new StringBuilder();
        for (int index = 0; index < 5000; index++) page.append("<li>item ").append(index);
        String text = html(page.toString());
        assertTrue(text.startsWith("- item 0\n- item 1\n"));
        assertTrue(text.endsWith("- item 4999"));
        assertFalse(text.contains("<"));
    }
}
