package com.luutry.agent.rag.parser;

import com.luutry.agent.rag.model.RagDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlainTextParser.
 * Covers: parsing, version extraction, date extraction, heading counting, format support.
 */
@DisplayName("PlainTextParser Tests")
class PlainTextParserTest {

    private PlainTextParser parser;

    @BeforeEach
    void setUp() {
        parser = new PlainTextParser();
    }

    @Test
    @DisplayName("parse - basic document parsing")
    void testParseBasic() {
        RagDocument doc = parser.parse("考勤管理制度", "员工考勤管理规定内容...", "attendance");
        assertNotNull(doc);
        assertEquals("考勤管理制度", doc.getTitle());
        assertEquals("attendance", doc.getCategory());
        assertEquals("v1.0", doc.getVersion());
    }

    @Test
    @DisplayName("parse - extracts version from content")
    void testParseVersion() {
        String content = "版本: v2.1\n生效日期: 2024-06-01\n员工考勤管理规定...";
        RagDocument doc = parser.parse("考勤制度", content, "attendance");
        assertEquals("v2.1", doc.getVersion());
    }

    @Test
    @DisplayName("parse - extracts date from content")
    void testParseDate() {
        String content = "2024-03-15 发布\n关于考勤管理的通知...";
        RagDocument doc = parser.parse("通知", content, "notice");
        assertNotNull(doc.getEffectiveDate());
        assertEquals(2024, doc.getEffectiveDate().getYear());
        assertEquals(3, doc.getEffectiveDate().getMonthValue());
    }

    @Test
    @DisplayName("parse - null title throws NPE")
    void testParseNullTitle() {
        assertThrows(NullPointerException.class, () ->
                parser.parse(null, "content", "category"));
    }

    @Test
    @DisplayName("parse - null content throws NPE")
    void testParseNullContent() {
        assertThrows(NullPointerException.class, () ->
                parser.parse("title", null, "category"));
    }

    @Test
    @DisplayName("supports - plaintext and markdown")
    void testSupports() {
        assertTrue(parser.supports("plaintext"));
        assertTrue(parser.supports("markdown"));
        assertTrue(parser.supports("PLAINTEXT"));
        assertFalse(parser.supports("pdf"));
        assertFalse(parser.supports("word"));
    }

    @Test
    @DisplayName("countHeadings - counts markdown headings")
    void testCountHeadings() {
        String content = "# 第一章\n内容\n## 第一节\n内容\n### 条款1\n内容\n## 第二节\n内容";
        assertEquals(4, parser.countHeadings(content));
    }

    @Test
    @DisplayName("countHeadings - zero for plain text")
    void testCountHeadingsPlainText() {
        assertEquals(0, parser.countHeadings("Just plain text without headings"));
    }

    @Test
    @DisplayName("countHeadings - null/empty returns 0")
    void testCountHeadingsEmpty() {
        assertEquals(0, parser.countHeadings(null));
        assertEquals(0, parser.countHeadings(""));
        assertEquals(0, parser.countHeadings("   "));
    }
}
