package com.mudassirshahzad.eka.api.controller;

import com.mudassirshahzad.eka.api.dto.DocumentResponse;
import com.mudassirshahzad.eka.api.dto.PageResponse;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationToken;
import com.mudassirshahzad.eka.api.security.RequireRole;
import com.mudassirshahzad.eka.application.document.DeleteDocumentCommand;
import com.mudassirshahzad.eka.application.document.DeleteDocumentUseCase;
import com.mudassirshahzad.eka.application.document.GetDocumentUseCase;
import com.mudassirshahzad.eka.application.document.ListDocumentsUseCase;
import com.mudassirshahzad.eka.application.document.UploadDocumentCommand;
import com.mudassirshahzad.eka.application.document.UploadDocumentUseCase;
import com.mudassirshahzad.eka.application.shared.ApplicationException;
import com.mudassirshahzad.eka.domain.document.Document;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.document.DocumentMetadata;
import com.mudassirshahzad.eka.domain.document.SupportedFormat;
import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST surface for the existing document-ingestion pipeline (P06.1) — reuses
 * {@code UploadDocumentUseCase}/{@code GetDocumentUseCase}/{@code ListDocumentsUseCase}/
 * {@code DeleteDocumentUseCase} exactly as they already were; no ingestion business logic lives
 * here. Closes the "no REST endpoint for document ingestion" gap tracked since the post-Phase-5
 * audit (finding H2) and formally deferred at v0.6.1 (ADR EX09).
 *
 * <p>{@code getDocument}/{@code listDocuments} are tenant-scoped, not owner-scoped — matching
 * {@code DocumentApplicationService}'s existing, unchanged semantics: documents are shared
 * tenant knowledge-base content, not private per-user resources like conversations. This
 * milestone does not add per-document authorization beyond the tenant boundary; that is the
 * Authorization Filter, explicitly out of scope here (frozen roadmap, Phase 6 vs. later phases).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final UploadDocumentUseCase uploadDocumentUseCase;
    private final GetDocumentUseCase    getDocumentUseCase;
    private final ListDocumentsUseCase  listDocumentsUseCase;
    private final DeleteDocumentUseCase deleteDocumentUseCase;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole({UserRole.USER, UserRole.ADMIN})
    public ResponseEntity<DocumentResponse> uploadDocument(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) String tags) {

        JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication;

        if (file.isEmpty()) {
            throw new ApplicationException("file must not be empty");
        }

        String filename = file.getOriginalFilename();
        SupportedFormat format = SupportedFormat.fromFilename(filename)
                .or(() -> SupportedFormat.fromMimeType(file.getContentType()))
                .orElseThrow(() -> new ApplicationException(
                        "Unsupported or undetectable document format for: " + filename));

        DocumentMetadata metadata = DocumentMetadata.builder()
                .title(title)
                .department(department)
                .classification(classification)
                .tags(parseTags(tags))
                .build();

        Document document = uploadDocumentUseCase.execute(new UploadDocumentCommand(
                principal.tenantId(), principal.userId(), filename, format, metadata, readBytes(file)));

        DocumentResponse response = DocumentResponse.from(document);
        return ResponseEntity.created(URI.create("/api/v1/documents/" + response.id())).body(response);
    }

    @GetMapping("/{documentId}")
    public DocumentResponse getDocument(Authentication authentication, @PathVariable UUID documentId) {
        JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication;
        Document document = getDocumentUseCase.execute(DocumentId.of(documentId), principal.tenantId());
        return DocumentResponse.from(document);
    }

    @GetMapping
    public PageResponse<DocumentResponse> listDocuments(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication;
        PageResult<Document> result = listDocumentsUseCase.execute(
                principal.tenantId(), PageRequest.of(page, size));
        return PageResponse.from(result, DocumentResponse::from);
    }

    @DeleteMapping("/{documentId}")
    @RequireRole({UserRole.USER, UserRole.ADMIN})
    public ResponseEntity<Void> deleteDocument(Authentication authentication, @PathVariable UUID documentId) {
        JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication;
        deleteDocumentUseCase.execute(new DeleteDocumentCommand(
                DocumentId.of(documentId), principal.tenantId(), principal.userId()));
        return ResponseEntity.noContent().build();
    }

    private static Set<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApplicationException("Failed to read uploaded file", e);
        }
    }
}
