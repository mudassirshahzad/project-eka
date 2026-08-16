package com.mudassirshahzad.eka.application.document;

import com.mudassirshahzad.eka.application.event.DocumentIndexedEvent;
import com.mudassirshahzad.eka.application.event.DocumentParsedEvent;
import com.mudassirshahzad.eka.application.shared.ApplicationException;
import com.mudassirshahzad.eka.application.shared.DomainEventPublisher;
import com.mudassirshahzad.eka.domain.chunk.Chunk;
import com.mudassirshahzad.eka.domain.document.Document;
import com.mudassirshahzad.eka.domain.document.DocumentParser;
import com.mudassirshahzad.eka.domain.document.FileStorage;
import com.mudassirshahzad.eka.domain.document.ParsedDocument;
import com.mudassirshahzad.eka.domain.document.SupportedFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Deliberately <b>not</b> {@code @Transactional} at the class level (P05.5, ADR HD01). Every
 * DB-touching step below (`documentService.registerDocument`, `chunkApplicationService.saveAll`,
 * `documentService.updateDocument`) calls a separate Spring bean that already carries its own
 * {@code @Transactional} boundary — so each gets its own short-lived transaction/connection, and
 * the slow steps in between (Tika parsing, file I/O, the Ollama embedding call, the Weaviate
 * indexing call) run holding no database connection at all. Before this fix, a single class-level
 * {@code @Transactional} here made all of that participate in one transaction, tying up a pooled
 * HikariCP connection for the entire duration of two external network calls per upload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadDocumentUseCase {

    private final DocumentApplicationService documentService;
    private final FileStorage                fileStorage;
    private final DocumentParser             documentParser;
    private final ChunkingService            chunkingService;
    private final EmbeddingService           embeddingService;
    private final ChunkApplicationService    chunkApplicationService;
    private final DocumentIndexingService    documentIndexingService;
    private final DomainEventPublisher       eventPublisher;

    public Document execute(UploadDocumentCommand cmd) {
        Objects.requireNonNull(cmd,            "command must not be null");
        Objects.requireNonNull(cmd.tenantId(), "tenantId must not be null");
        Objects.requireNonNull(cmd.ownerId(),  "ownerId must not be null");
        Objects.requireNonNull(cmd.metadata(), "metadata must not be null");

        if (cmd.filename() == null || cmd.filename().isBlank()) {
            throw new ApplicationException("filename must not be blank");
        }
        Objects.requireNonNull(cmd.format(), "format must not be null");

        if (cmd.content() == null) {
            throw new ApplicationException("content must not be null");
        }
        if (cmd.content().length == 0) {
            throw new ApplicationException("content must not be empty");
        }

        SupportedFormat detectedFormat = SupportedFormat.fromFilename(cmd.filename()).orElse(null);
        if (detectedFormat != null && detectedFormat != cmd.format()) {
            throw new ApplicationException(
                    "Format mismatch: filename '" + cmd.filename() + "' implies "
                    + detectedFormat + " but " + cmd.format() + " was declared");
        }

        log.debug("Uploading document: filename={} format={} tenant={}",
                cmd.filename(), cmd.format(), cmd.tenantId());

        // 1. Register — its own short transaction (DocumentApplicationService is @Transactional);
        //    committed and visible immediately, independent of everything that follows
        RegisterDocumentCommand registerCmd = new RegisterDocumentCommand(
                cmd.tenantId(), cmd.ownerId(), cmd.filename(), cmd.format(), cmd.metadata());
        Document document = documentService.registerDocument(registerCmd);

        try {
            // 2. Begin parsing
            document.startParsing();

            // 3. Parse with Tika
            ParsedDocument parsed = documentParser.parse(cmd.content(), cmd.format());
            log.debug("Parsed document: id={} status={} chars={}",
                    document.getId(), parsed.status(), parsed.metadata().characterCount());

            // 4. Persist raw file
            String rawPath = rawRelativePath(document);
            fileStorage.store(rawPath, cmd.content());

            // 5. Persist extracted text
            String parsedPath = parsedRelativePath(document);
            fileStorage.store(parsedPath, parsed.extractedText().getBytes(StandardCharsets.UTF_8));

            // 6. Assign storage paths
            document.assignContentPath(rawPath);
            document.assignParsedTextPath(parsedPath);

            // 7. Transition to CHUNKING
            document.startChunking();

            // 8. Chunk the extracted text
            List<Chunk> chunks = chunkingService.chunk(parsed, document.getId(), document.getTenantId());
            log.debug("Chunked document: id={} chunks={}", document.getId(), chunks.size());

            // 9. Transition to EMBEDDING
            document.startEmbedding();

            // 10. Embed chunks — assigns provenance; vectors are returned for single-use indexing
            List<EmbeddedChunk> embeddedChunks = embeddingService.embed(chunks);

            // 11. Persist chunks with embedding provenance — its own short transaction
            //     (ChunkApplicationService is @Transactional); vectors are preserved in the result
            List<EmbeddedChunk> savedEmbeddedChunks = chunkApplicationService.saveAll(embeddedChunks);

            // 12. Index in Weaviate using pre-computed vectors — no second embedding call
            List<Chunk> indexedChunks = documentIndexingService.index(savedEmbeddedChunks);

            // 13. Transition document to INDEXED
            document.markIndexed(indexedChunks.size());

            // 14. Persist final document state — its own short transaction
            document = documentService.updateDocument(document);

            eventPublisher.publish(new DocumentParsedEvent(
                    document.getId(), document.getTenantId(), parsed.detectedFormat(), parsedPath));
            eventPublisher.publish(new DocumentIndexedEvent(
                    document.getId(), document.getTenantId(), indexedChunks.size()));

            log.info("Document fully ingested: id={} filename={} chunks={} status={}",
                    document.getId(), document.getFilename(),
                    indexedChunks.size(), document.getStatus());
            return document;
        } catch (RuntimeException ex) {
            // Steps 2-14 no longer share a transaction with step 1 (ADR HD01), so a failure here
            // would otherwise leave the document silently stuck in whatever PENDING/PARSING/
            // CHUNKING/EMBEDDING status it last reached — indistinguishable from "still in
            // progress." Document.markFailed(String) has existed since the domain model was
            // designed but was never actually called; this is the first caller.
            log.error("Document ingestion failed: id={} filename={} lastStatus={}",
                    document.getId(), document.getFilename(), document.getStatus(), ex);
            document.markFailed(ex.getMessage());
            documentService.updateDocument(document);
            throw ex;
        }
    }

    private String rawRelativePath(Document document) {
        String safeFilename = document.getFilename().replaceAll("[/\\\\]", "_");
        return document.getTenantId().value() + "/"
             + document.getId().value() + "/raw/"
             + safeFilename;
    }

    private String parsedRelativePath(Document document) {
        return document.getTenantId().value() + "/"
             + document.getId().value() + "/parsed.txt";
    }
}
