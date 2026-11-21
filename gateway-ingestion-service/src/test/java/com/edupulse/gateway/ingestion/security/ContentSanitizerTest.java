package com.edupulse.gateway.ingestion.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentSanitizerTest {

    private final ContentSanitizer sanitizer = new ContentSanitizer();

    @Test
    void keepsPlainContent() {
        String html = sanitizer.sanitizeHtml("<h1>Policy</h1><p>Student records are <b>private</b>.</p>");
        assertTrue(html.contains("<h1>Policy</h1>"));
        assertTrue(html.contains("<b>private</b>"));
    }

    @Test
    void removesScriptsAndTheirContent() {
        String html = sanitizer.sanitizeHtml("<p>ok</p><script>alert('x')</script>");
        assertFalse(html.contains("script"));
        assertFalse(html.contains("alert"));
        assertTrue(html.contains("ok"));
    }

    @Test
    void removesEventHandlersAndJavascriptUrls() {
        String html = sanitizer.sanitizeHtml(
                "<a href=\"javascript:alert(1)\" onclick=\"steal()\">link</a><p onmouseover=\"x()\">t</p>");
        assertFalse(html.contains("javascript:"));
        assertFalse(html.contains("onclick"));
        assertFalse(html.contains("onmouseover"));
        assertTrue(html.contains("link"));
    }

    @Test
    void removesActiveElements() {
        String html = sanitizer.sanitizeHtml(
                "<iframe src=\"https://evil.test\"></iframe><object data=\"x\"></object><embed src=\"x\">"
                        + "<style>body{}</style><form action=\"/x\"><input></form><p>text</p>");
        assertFalse(html.contains("iframe"));
        assertFalse(html.contains("object"));
        assertFalse(html.contains("embed"));
        assertFalse(html.contains("<style"));
        assertFalse(html.contains("<form"));
        assertTrue(html.contains("text"));
    }

    @Test
    void textExtractionContainsNoScriptText() {
        assertEquals("Hello world", sanitizer.sanitizeToText("<p>Hello</p><script>evil()</script><p>world</p>"));
    }

    @Test
    void handlesNullAndEmpty() {
        assertEquals("", sanitizer.sanitizeToText(null));
        assertEquals("", sanitizer.sanitizeToText(""));
    }
}
