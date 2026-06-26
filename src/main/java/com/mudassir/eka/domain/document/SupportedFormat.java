package com.mudassir.eka.domain.document;

import java.util.Arrays;
import java.util.Optional;

public enum SupportedFormat {
    PDF, DOCX, PPTX, XLSX, TXT, CSV, HTML, MARKDOWN;

    public static Optional<SupportedFormat> fromFilename(String filename) {
        if (filename == null) return Optional.empty();
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))  return Optional.of(PDF);
        if (lower.endsWith(".docx")) return Optional.of(DOCX);
        if (lower.endsWith(".pptx")) return Optional.of(PPTX);
        if (lower.endsWith(".xlsx")) return Optional.of(XLSX);
        if (lower.endsWith(".txt"))  return Optional.of(TXT);
        if (lower.endsWith(".csv"))  return Optional.of(CSV);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return Optional.of(HTML);
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return Optional.of(MARKDOWN);
        return Optional.empty();
    }

    public boolean isTabular() {
        return this == XLSX || this == CSV;
    }

    public boolean isPresentation() {
        return this == PPTX;
    }

    public boolean isRichText() {
        return this == PDF || this == DOCX;
    }
}
