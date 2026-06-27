package com.mudassir.eka.application.document;

import com.mudassir.eka.application.event.DocumentIndexedEvent;
import com.mudassir.eka.application.event.DocumentParsedEvent;
import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.application.shared.DomainEventPublisher;
import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.document.Document;
import com.mudassir.eka.domain.document.DocumentParser;
import com.mudassir.eka.domain.document.FileStorage;
import com.mudassir.eka.domain.document.ParsedDocument;
import com.mudassir.eka.domain.document.SupportedFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
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

        // 1. Register — DB INSERT with PENDING status; publishes DocumentRegisteredEvent
        RegisterDocumentCommand registerCmd = new RegisterDocumentCommand(
                cmd.tenantId(), cmd.ownerId(), cmd.filename(), cmd.format(), cmd.metadata());
        Document document = documentService.registerDocument(registerCmd);

        // 2. Begin parsing
        document.startParsing();

        // 3. Parse with Tika — failure here rolls back the INSERT from step 1
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

        // 10. Embed chunks in memory — assigns provenance to each chunk domain object
        List<EmbeddedChunk> embeddedChunks = embeddingService.embed(chunks);

        // 11. Persist chunks with embedding provenance (vectorId still null at this point)
        List<Chunk> savedChunks = chunkApplicationService.saveAll(embeddedChunks);

        // 12. Index in Weaviate — assigns vectorIds; persists them to DB
        List<Chunk> indexedChunks = documentIndexingService.index(savedChunks);

        // 13. Transition document to INDEXED
        document.markIndexed(indexedChunks.size());

        // 14. Persist final document state (INDEXED, all paths and count set)
        document = documentService.updateDocument(document);

        eventPublisher.publish(new DocumentParsedEvent(
                document.getId(), document.getTenantId(), parsed.detectedFormat(), parsedPath));
        eventPublisher.publish(new DocumentIndexedEvent(
                document.getId(), document.getTenantId(), indexedChunks.size()));

        log.info("Document fully ingested: id={} filename={} chunks={} status={}",
                document.getId(), document.getFilename(),
                indexedChunks.size(), document.getStatus());
        return document;
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
