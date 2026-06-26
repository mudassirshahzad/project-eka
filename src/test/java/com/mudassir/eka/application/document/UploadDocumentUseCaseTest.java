package com.mudassir.eka.application.document;

import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.domain.document.Document;
import com.mudassir.eka.domain.document.DocumentMetadata;
import com.mudassir.eka.domain.document.SupportedFormat;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadDocumentUseCaseTest {

    @Mock  private DocumentApplicationService documentService;
    @InjectMocks private UploadDocumentUseCase useCase;

    private final TenantId       tenantId = TenantId.generate();
    private final UserId         ownerId  = UserId.generate();
    private final DocumentMetadata metadata = DocumentMetadata.EMPTY;

    @Test
    void execute_rejectsNullCommand() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(null));
    }

    @Test
    void execute_rejectsNullTenantId() {
        var cmd = new RegisterDocumentCommand(null, ownerId, "file.pdf", SupportedFormat.PDF, metadata);
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(cmd));
    }

    @Test
    void execute_rejectsBlankFilename() {
        var cmd = new RegisterDocumentCommand(tenantId, ownerId, "   ", SupportedFormat.PDF, metadata);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("filename");
    }

    @Test
    void execute_rejectsNullFilename() {
        var cmd = new RegisterDocumentCommand(tenantId, ownerId, null, SupportedFormat.PDF, metadata);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("filename");
    }

    @Test
    void execute_rejectsNullFormat() {
        var cmd = new RegisterDocumentCommand(tenantId, ownerId, "report.pdf", null, metadata);
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(cmd));
    }

    @Test
    void execute_rejectsFormatFilenameExtensionMismatch() {
        // report.pdf implies PDF, but DOCX was declared
        var cmd = new RegisterDocumentCommand(tenantId, ownerId, "report.pdf", SupportedFormat.DOCX, metadata);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("Format mismatch");
    }

    @Test
    void execute_delegatesWhenFormatMatchesExtension() {
        var cmd = new RegisterDocumentCommand(tenantId, ownerId, "report.pdf", SupportedFormat.PDF, metadata);
        Document saved = Document.create(tenantId, ownerId, "report.pdf", SupportedFormat.PDF, metadata);
        when(documentService.registerDocument(cmd)).thenReturn(saved);

        Document result = useCase.execute(cmd);

        assertThat(result).isSameAs(saved);
        verify(documentService).registerDocument(cmd);
    }

    @Test
    void execute_delegatesWhenExtensionUnknown() {
        // .xyz has no known mapping — any declared format is accepted
        var cmd = new RegisterDocumentCommand(tenantId, ownerId, "archive.xyz", SupportedFormat.TXT, metadata);
        Document saved = Document.create(tenantId, ownerId, "archive.xyz", SupportedFormat.TXT, metadata);
        when(documentService.registerDocument(cmd)).thenReturn(saved);

        Document result = useCase.execute(cmd);

        assertThat(result).isSameAs(saved);
    }
}
