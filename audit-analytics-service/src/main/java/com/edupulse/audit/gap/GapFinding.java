package com.edupulse.audit.gap;

/** status: GAP (cerinta neacoperita) sau NOT_ASSESSED (evaluarea a esuat, necesita verificare manuala). */
public record GapFinding(String section, String requirement, String status, String severity,
                         String finding, String evidence) {

    public static final String GAP = "GAP";
    public static final String NOT_ASSESSED = "NOT_ASSESSED";
}
