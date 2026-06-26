package com.mudassir.eka.infrastructure.parsing;

import com.mudassir.eka.domain.document.DocumentParser;
import com.mudassir.eka.domain.document.ParsedDocument;
import com.mudassir.eka.domain.document.ParsedMetadata;
import com.mudassir.eka.domain.document.ParsingStatus;
import com.mudassir.eka.domain.document.SupportedFormat;
import com.mudassir.eka.infrastructure.parsing.exception.DocumentParsingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;

@Slf4j
@Component
public class TikaDocumentParserAdapter implements DocumentParser {

    private final AutoDetectParser parser = new AutoDetectParser();

    @Override
    public ParsedDocument parse(byte[] content, SupportedFormat format) {
        BodyContentHandler handler  = new BodyContentHandler(-1);
        Metadata           metadata = new Metadata();
        ParseContext       context  = new ParseContext();
        context.set(Parser.class, parser);

        try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
            parser.parse(in, handler, metadata, context);
        } catch (IOException | SAXException | TikaException e) {
            log.warn("Tika failed to parse document of format {}: {}", format, e.getMessage());
            throw new DocumentParsingException(
                    "Failed to parse document of format " + format + ": " + e.getMessage(), e);
        }

        String extractedText  = handler.toString();
        SupportedFormat detected = SupportedFormat.fromMimeType(metadata.get(Metadata.CONTENT_TYPE))
                .orElse(format);
        ParsingStatus   status   = extractedText.isBlank() ? ParsingStatus.PARTIAL : ParsingStatus.SUCCESS;

        return new ParsedDocument(
                extractedText,
                buildMetadata(metadata, extractedText),
                detected,
                status,
                Instant.now()
        );
    }

    private ParsedMetadata buildMetadata(Metadata metadata, String extractedText) {
        String title       = metadata.get(TikaCoreProperties.TITLE);
        String author      = metadata.get(TikaCoreProperties.CREATOR);
        String description = metadata.get(TikaCoreProperties.DESCRIPTION);
        int    pageCount   = parseIntOrZero(metadata.get("meta:page-count"));

        return new ParsedMetadata(title, author, description, pageCount, extractedText.length());
    }

    private int parseIntOrZero(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
