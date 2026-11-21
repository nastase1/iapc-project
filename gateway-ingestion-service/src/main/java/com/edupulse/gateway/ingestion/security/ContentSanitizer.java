package com.edupulse.gateway.ingestion.security;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/** Strips scripts and active content from ingested pages (before text extraction). */
@Component
public class ContentSanitizer {

    /** @return HTML without script, style, iframe, object/embed, on* handlers and javascript: URLs */
    public String sanitizeHtml(String html) {
        Document dirty = Jsoup.parse(html == null ? "" : html);
        dirty.select("script, style, noscript, iframe, frame, object, embed, applet, form, template").remove();
        Safelist safelist = Safelist.relaxed()
                .removeTags("img")
                .addProtocols("a", "href", "http", "https");
        return Jsoup.clean(dirty.body().html(), safelist);
    }

    /** @return only the visible text, after sanitization */
    public String sanitizeToText(String html) {
        return Jsoup.parse(sanitizeHtml(html)).text();
    }
}
