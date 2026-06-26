package com.mudassir.eka.domain.document;

import java.util.Optional;

public enum SupportedFormat {
    PDF, DOCX, DOC, PPTX, XLSX, TXT, CSV, HTML, MARKDOWN, JSON, XML;

    public static Optional<SupportedFormat> fromFilename(String filename) {
        if (filename == null) return Optional.empty();
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))                        return Optional.of(PDF);
        if (lower.endsWith(".docx"))                       return Optional.of(DOCX);
        if (lower.endsWith(".doc"))                        return Optional.of(DOC);
        if (lower.endsWith(".pptx"))                       return Optional.of(PPTX);
        if (lower.endsWith(".xlsx"))                       return Optional.of(XLSX);
        if (lower.endsWith(".txt"))                        return Optional.of(TXT);
        if (lower.endsWith(".csv"))                        return Optional.of(CSV);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return Optional.of(HTML);
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return Optional.of(MARKDOWN);
        if (lower.endsWith(".json"))                       return Optional.of(JSON);
        if (lower.endsWith(".xml"))                        return Optional.of(XML);
        return Optional.empty();
    }

    public static Optional<SupportedFormat> fromMimeType(String mimeType) {
        if (mimeType == null) return Optional.empty();
        int semicolonIdx = mimeType.indexOf(';');
        String base = (semicolonIdx >= 0 ? mimeType.substring(0, semicolonIdx) : mimeType).trim().toLowerCase();
        return switch (base) {
            case "application/pdf" -> Optional.of(PDF);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> Optional.of(DOCX);
            case "application/msword" -> Optional.of(DOC);
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> Optional.of(PPTX);
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> Optional.of(XLSX);
            case "text/plain" -> Optional.of(TXT);
            case "text/csv", "application/csv" -> Optional.of(CSV);
            case "text/html" -> Optional.of(HTML);
            case "text/markdown", "text/x-markdown" -> Optional.of(MARKDOWN);
            case "application/json" -> Optional.of(JSON);
            case "application/xml", "text/xml" -> Optional.of(XML);
            default -> Optional.empty();
        };
    }

    public boolean isTabular() {
        return this == XLSX || this == CSV;
    }

    public boolean isPresentation() {
        return this == PPTX;
    }

    public boolean isRichText() {
        return this == PDF || this == DOCX || this == DOC;
    }
}
