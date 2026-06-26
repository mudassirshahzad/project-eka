package com.mudassir.eka.application.document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeleteDocumentUseCase {

    private final DocumentApplicationService documentService;

    public void execute(DeleteDocumentCommand cmd) {
        Objects.requireNonNull(cmd, "command must not be null");
        Objects.requireNonNull(cmd.documentId(), "documentId must not be null");
        Objects.requireNonNull(cmd.tenantId(), "tenantId must not be null");
        Objects.requireNonNull(cmd.deletedBy(), "deletedBy must not be null");

        log.debug("Deleting document: id={} tenant={} by={}",
                cmd.documentId(), cmd.tenantId(), cmd.deletedBy());
        documentService.deleteDocument(cmd);
    }
}
