package com.mudassirshahzad.eka.infrastructure.parsing;

import com.mudassirshahzad.eka.domain.document.DocumentParser;
import com.mudassirshahzad.eka.domain.document.ParsedDocument;
import com.mudassirshahzad.eka.domain.document.ParsedMetadata;
import com.mudassirshahzad.eka.domain.document.ParsingStatus;
import com.mudassirshahzad.eka.domain.document.SupportedFormat;
import com.mudassirshahzad.eka.infrastructure.parsing.exception.DocumentParsingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

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

        try (TikaInputStream in = TikaInputStream.get(content)) {
            parser.parse(in, handler, metadata, context);
        } catch (IOException | SAXException | TikaException e) {
            log.warn("Tika failed to parse document of format {}: {}", format, e.getMessage());
            throw new DocumentParsingException(
                    "Failed to parse document of format " + format + ": " + e.getMessage(), e);
        }

        String extractedText  = handler.toString();
        SupportedFormat detected = SupportedFormat.fromMimeType(metadata.get(HttpHeaders.CONTENT_TYPE))
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
        int    pageCount   = extractPageCount(metadata);

        return new ParsedMetadata(title, author, description, pageCount, extractedText.length());
    }

    /**
     * Resolves the page count across Tika's two page-count keys.
     *
     * <p>Tika does not use one key for every format. {@code PagedText.N_PAGES}
     * ({@code xmpTPg:NPages}) is what the PDF parser populates; {@code Office.PAGE_COUNT}
     * ({@code meta:page-count}) is what the OOXML/Office parsers populate. Office documents
     * set <em>both</em> to the same value, PDFs set only the former — so reading
     * {@code N_PAGES} first and falling back to {@code PAGE_COUNT} covers both families
     * without changing what Office formats already reported.
     */
    private int extractPageCount(Metadata metadata) {
        int pagedText = parseIntOrZero(metadata.get(PagedText.N_PAGES));
        return pagedText > 0 ? pagedText : parseIntOrZero(metadata.get(Office.PAGE_COUNT));
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
