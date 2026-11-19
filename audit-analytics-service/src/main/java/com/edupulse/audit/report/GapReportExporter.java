package com.edupulse.audit.report;

import com.edupulse.audit.gap.GapFinding;
import com.edupulse.audit.gap.GapReport;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;

/** Exporta un raport de gap analysis in CSV si PDF (JSON il serializeaza direct controllerul). */
public final class GapReportExporter {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);

    private GapReportExporter() {
    }

    public static String toCsv(GapReport report) {
        StringBuilder sb = new StringBuilder("section,status,severity,finding,requirement,evidence\n");
        for (GapFinding f : report.findings()) {
            sb.append(String.join(",", CsvWriter.cell(f.section()), CsvWriter.cell(f.status()),
                    CsvWriter.cell(f.severity()), CsvWriter.cell(f.finding()),
                    CsvWriter.cell(f.requirement()), CsvWriter.cell(f.evidence()))).append('\n');
        }
        return sb.toString();
    }

    public static byte[] toPdf(GapReport report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Compliance Gap Analysis Report", TITLE));
            doc.add(new Paragraph("Framework: " + clean(report.framework()), BODY));
            doc.add(new Paragraph("Report: " + report.id() + "   Status: " + report.status()
                    + "   Generated: " + report.createdAt(), BODY));
            doc.add(new Paragraph("Chunks analysed: " + report.analyzedChunks() + " / " + report.totalChunks()
                    + "   Not assessed: " + report.failedChunks() + "   Findings: " + report.findings().size(), BODY));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[]{2f, 1.2f, 1f, 4f, 4f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String h : new String[]{"Section", "Status", "Severity", "Finding", "Evidence"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, HEADER));
                c.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(c);
            }
            for (GapFinding f : report.findings()) {
                table.addCell(new Phrase(clean(f.section()), BODY));
                table.addCell(new Phrase(clean(f.status()), BODY));
                table.addCell(new Phrase(clean(f.severity()), BODY));
                table.addCell(new Phrase(clean(f.finding()), BODY));
                table.addCell(new Phrase(clean(f.evidence()), BODY));
            }
            doc.add(table);
        } catch (DocumentException e) {
            throw new IllegalStateException("PDF generation failed", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return out.toByteArray();
    }

    /** Elimina caracterele de control; fontul standard PDF nu acopera tot Unicode-ul. */
    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\p{Cntrl}&&[^\n]]", " ");
    }
}
