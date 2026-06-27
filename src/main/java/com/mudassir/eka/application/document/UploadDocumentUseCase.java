package com.mudassir.eka.application.document;

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

        // 3. Parse with Tika — if this throws, @Transactional rolls back the INSERT from step 1
        //    and no files are written, so there are no orphaned artifacts
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

        // 10. Embed chunks and assign provenance in-memory
        List<EmbeddedChunk> embeddedChunks = embeddingService.embed(chunks);

        // 11. Persist all chunks (INSERT with embedding provenance already set)
        List<Chunk> savedChunks = chunkApplicationService.saveAll(embeddedChunks);

        // 12. Update document chunk count
        document.updateChunkCount(savedChunks.size());

        // 13. Persist document (status=EMBEDDING, paths set, chunkCount set)
        document = documentService.updateDocument(document);

        eventPublisher.publish(new DocumentParsedEvent(
                document.getId(), document.getTenantId(),
                parsed.detectedFormat(), parsedPath));

        log.info("Document ingested: id={} filename={} chunks={} status={}",
                document.getId(), document.getFilename(),
                savedChunks.size(), document.getStatus());
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
