package com.mudassir.eka.infrastructure.parsing;

import com.mudassir.eka.domain.document.ParsedDocument;
import com.mudassir.eka.domain.document.ParsingStatus;
import com.mudassir.eka.domain.document.SupportedFormat;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TikaDocumentParserAdapterTest {

    private final TikaDocumentParserAdapter adapter = new TikaDocumentParserAdapter();

    @Test
    void parse_extractsTextFromPlainTextContent() {
        byte[] content = "Hello, World! This is a test document.".getBytes(StandardCharsets.UTF_8);

        ParsedDocument result = adapter.parse(content, SupportedFormat.TXT);

        assertThat(result.extractedText()).contains("Hello, World!");
        assertThat(result.status()).isEqualTo(ParsingStatus.SUCCESS);
    }

    @Test
    void parse_stripsHtmlTagsAndExtractsVisibleText() {
        byte[] content = "<html><body><h1>Title</h1><p>Body text here.</p></body></html>"
                .getBytes(StandardCharsets.UTF_8);

        ParsedDocument result = adapter.parse(content, SupportedFormat.HTML);

        assertThat(result.extractedText()).contains("Title");
        assertThat(result.extractedText()).contains("Body text here.");
    }

    @Test
    void parse_extractsTextFromXmlElements() {
        byte[] content = "<?xml version=\"1.0\"?><root><item>Sample content</item></root>"
                .getBytes(StandardCharsets.UTF_8);

        ParsedDocument result = adapter.parse(content, SupportedFormat.XML);

        assertThat(result.extractedText()).contains("Sample content");
        assertThat(result.status()).isEqualTo(ParsingStatus.SUCCESS);
    }

    @Test
    void parse_handlesJsonContent() {
        byte[] content = "{\"message\": \"hello from json\"}".getBytes(StandardCharsets.UTF_8);

        ParsedDocument result = adapter.parse(content, SupportedFormat.JSON);

        assertThat(result).isNotNull();
        assertThat(result.status()).isIn(ParsingStatus.SUCCESS, ParsingStatus.PARTIAL);
    }

    @Test
    void parse_returnsPartialStatusWhenExtractedTextIsBlank() {
        byte[] content = "   ".getBytes(StandardCharsets.UTF_8);

        ParsedDocument result = adapter.parse(content, SupportedFormat.TXT);

        assertThat(result.status()).isEqualTo(ParsingStatus.PARTIAL);
    }

    @Test
    void parse_setsCharacterCountMatchingExtractedTextLength() {
        String text = "Character counting test.";
        byte[] content = text.getBytes(StandardCharsets.UTF_8);

        ParsedDocument result = adapter.parse(content, SupportedFormat.TXT);

        assertThat(result.metadata().characterCount()).isEqualTo(result.extractedText().length());
    }

    @Test
    void parse_setsParsedAtTimestampAtOrAfterCallTime() {
        byte[] content = "timestamp test".getBytes(StandardCharsets.UTF_8);
        Instant before = Instant.now();

        ParsedDocument result = adapter.parse(content, SupportedFormat.TXT);

        assertThat(result.parsedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void parse_setsFallbackFormatWhenMimeTypeUnrecognised() {
        byte[] content = "plain text".getBytes(StandardCharsets.UTF_8);

        ParsedDocument result = adapter.parse(content, SupportedFormat.MARKDOWN);

        assertThat(result.detectedFormat()).isNotNull();
    }
}
