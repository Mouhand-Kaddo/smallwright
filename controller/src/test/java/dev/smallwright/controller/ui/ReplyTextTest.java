package dev.smallwright.controller.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.compose.ui.text.AnnotatedString;
import androidx.compose.ui.text.font.FontWeight;
import java.util.List;
import org.junit.Test;

public final class ReplyTextTest {
    @Test
    public void boldAndCodeLoseTheirMarksAndKeepTheirStyle() {
        List<ReplyBlock> blocks = ReplyText.parse("Now **swipe** to delete a `task` today.");
        assertEquals(1, blocks.size());
        AnnotatedString text = blocks.get(0).getText();
        assertEquals("Now swipe to delete a task today.", text.getText());
        assertEquals(2, text.getSpanStyles().size());
        AnnotatedString.Range<?> bold = text.getSpanStyles().get(0);
        assertEquals(4, bold.getStart());
        assertEquals(9, bold.getEnd());
        assertEquals(
                FontWeight.Companion.getBold(),
                text.getSpanStyles().get(0).getItem().getFontWeight());
        assertNotNull(text.getSpanStyles().get(1).getItem().getFontFamily());
        assertEquals(22, text.getSpanStyles().get(1).getStart());
        assertEquals(26, text.getSpanStyles().get(1).getEnd());
    }

    @Test
    public void bulletsAndNumbersBecomeItemsAndParagraphsStayApart() {
        List<ReplyBlock> blocks =
                ReplyText.parse(
                        "Here is what changed:\n\n- **List:** shows tasks\n* Delete works\n"
                                + "1. First\n2) Second\n\nAll done.\nEnjoy.");
        assertEquals(
                List.of("", "•", "•", "1.", "2)", ""),
                blocks.stream()
                        .map(ReplyBlock::getMarker)
                        .collect(java.util.stream.Collectors.toList()));
        assertEquals("Here is what changed:", blocks.get(0).getText().getText());
        assertEquals("List: shows tasks", blocks.get(1).getText().getText());
        assertEquals("Delete works", blocks.get(2).getText().getText());
        assertEquals("First", blocks.get(3).getText().getText());
        assertEquals("Second", blocks.get(4).getText().getText());
        assertEquals("All done.\nEnjoy.", blocks.get(5).getText().getText());
    }

    @Test
    public void anythingElseStaysPlainExactlyAsWritten() {
        String plain =
                "# Title\n[link](http://x.test) ![i](a.png) <b>x</b> | a | b |\n"
                        + "unclosed ** here\na lone ` there\nempty `` ok\n2 * 3 and * star and ****";
        List<ReplyBlock> blocks = ReplyText.parse(plain);
        assertEquals(1, blocks.size());
        assertEquals(plain, blocks.get(0).getText().getText());
        assertEquals(0, blocks.get(0).getText().getSpanStyles().size());
    }

    @Test
    public void marksAreNeverMatchedAcrossLinesAndEmptyTextHasNoBlocks() {
        List<ReplyBlock> blocks = ReplyText.parse("a **b\nc** d `e\nf`");
        assertEquals("a **b\nc** d `e\nf`", blocks.get(0).getText().getText());
        assertEquals(0, blocks.get(0).getText().getSpanStyles().size());
        assertEquals(0, ReplyText.parse("  \n ").size());
    }

    @Test
    public void codeKeepsMarksInsideItAndBoldMayHoldCode() {
        List<ReplyBlock> blocks = ReplyText.parse("`**x**` and **use `y` here**");
        assertEquals("**x** and use y here", blocks.get(0).getText().getText());
        assertEquals(3, blocks.get(0).getText().getSpanStyles().size());
    }

    @Test
    public void singleStarsAroundWordsBecomeItalicButLooseStarsStay() {
        List<ReplyBlock> blocks = ReplyText.parse("- *Tip: keep it short* and **bold** with 2 * 3");
        AnnotatedString text = blocks.get(0).getText();
        assertEquals("Tip: keep it short and bold with 2 * 3", text.getText());
        assertEquals(2, text.getSpanStyles().size());
        assertEquals(0, text.getSpanStyles().get(0).getStart());
        assertEquals(18, text.getSpanStyles().get(0).getEnd());
        assertTrue(text.getSpanStyles().get(0).getItem().toString().contains("fontStyle=Italic"));
        assertEquals(
                FontWeight.Companion.getBold(),
                text.getSpanStyles().get(1).getItem().getFontWeight());
    }

    @Test
    public void aDividerLineOnlySeparatesParagraphs() {
        List<ReplyBlock> blocks = ReplyText.parse("Question one\n---\nQuestion two\n\n***\n- item");
        assertEquals(3, blocks.size());
        assertEquals("Question one", blocks.get(0).getText().getText());
        assertEquals("Question two", blocks.get(1).getText().getText());
        assertEquals("item", blocks.get(2).getText().getText());
    }
}
