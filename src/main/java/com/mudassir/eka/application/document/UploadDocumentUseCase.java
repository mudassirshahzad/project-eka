package com.mudassir.eka.application.document;

import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.domain.document.Document;
import com.mudassir.eka.domain.document.SupportedFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UploadDocumentUseCase {

    private final DocumentApplicationService documentService;

    public Document execute(RegisterDocumentCommand cmd) {
        Objects.requireNonNull(cmd, "command must not be null");
        Objects.requireNonNull(cmd.tenantId(), "tenantId must not be null");
        Objects.requireNonNull(cmd.ownerId(), "ownerId must not be null");
        Objects.requireNonNull(cmd.metadata(), "metadata must not be null");

        if (cmd.filename() == null || cmd.filename().isBlank()) {
            throw new ApplicationException("filename must not be blank");
        }
        Objects.requireNonNull(cmd.format(), "format must not be null");

        SupportedFormat detectedFormat = SupportedFormat.fromFilename(cmd.filename()).orElse(null);
        if (detectedFormat != null && detectedFormat != cmd.format()) {
            throw new ApplicationException(
                    "Format mismatch: filename '" + cmd.filename() + "' implies "
                    + detectedFormat + " but " + cmd.format() + " was declared");
        }

        log.debug("Uploading document: filename={} format={} tenant={}",
                cmd.filename(), cmd.format(), cmd.tenantId());
        return documentService.registerDocument(cmd);
    }
}
