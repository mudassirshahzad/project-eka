package com.mudassir.eka.domain.document;

public interface DocumentParser {

    ParsedDocument parse(byte[] content, SupportedFormat format);
}
