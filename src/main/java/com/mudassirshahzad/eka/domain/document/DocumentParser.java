package com.mudassirshahzad.eka.domain.document;

public interface DocumentParser {

    ParsedDocument parse(byte[] content, SupportedFormat format);
}
