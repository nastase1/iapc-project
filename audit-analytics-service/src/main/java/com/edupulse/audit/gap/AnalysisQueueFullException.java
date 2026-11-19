package com.edupulse.audit.gap;

public class AnalysisQueueFullException extends RuntimeException {

    public AnalysisQueueFullException() {
        super("Too many gap analyses in progress, retry later");
    }
}
