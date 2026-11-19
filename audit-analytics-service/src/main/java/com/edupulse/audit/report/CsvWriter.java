package com.edupulse.audit.report;

import com.edupulse.audit.audit.AuditEvent;

import java.util.List;

final class CsvWriter {

    private CsvWriter() {
    }

    static String toCsv(List<AuditEvent> events) {
        StringBuilder sb = new StringBuilder("id,tenant_id,user_id,event_type,query,answer,latency_ms,created_at\n");
        for (AuditEvent e : events) {
            sb.append(String.join(",", cell(e.id()), cell(e.tenantId()), cell(e.userId()), cell(e.eventType()),
                    cell(e.query()), cell(e.answer()), cell(e.latencyMs()), cell(e.createdAt()))).append('\n');
        }
        return sb.toString();
    }

    /** Escapare RFC 4180 + neutralizare formule (CSV injection: = + - @). */
    static String cell(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
