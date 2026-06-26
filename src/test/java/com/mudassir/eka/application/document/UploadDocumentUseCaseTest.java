package com.mudassir.eka.application.document;

import com.mudassir.eka.application.event.DocumentParsedEvent;
import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.application.shared.DomainEventPublisher;
import com.mudassir.eka.domain.document.Document;
import com.mudassir.eka.domain.document.DocumentMetadata;
import com.mudassir.eka.domain.document.DocumentParser;
import com.mudassir.eka.domain.document.FileStorage;
import com.mudassir.eka.domain.document.ParsedDocument;
import com.mudassir.eka.domain.document.ParsedMetadata;
import com.mudassir.eka.domain.document.ParsingStatus;
import com.mudassir.eka.domain.document.SupportedFormat;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadDocumentUseCaseTest {

    @Mock private DocumentApplicationService documentService;
    @Mock private FileStorage                fileStorage;
    @Mock private DocumentParser             documentParser;
    @Mock private DomainEventPublisher       eventPublisher;
    @InjectMocks private UploadDocumentUseCase useCase;

    private final TenantId         tenantId = TenantId.generate();
    private final UserId           ownerId  = UserId.generate();
    private final DocumentMetadata metadata = DocumentMetadata.EMPTY;
    private final byte[]           content  = "test content".getBytes(StandardCharsets.UTF_8);

    @Test
    void execute_rejectsNullCommand() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(null));
    }

    @Test
    void execute_rejectsNullTenantId() {
        var cmd = new UploadDocumentCommand(null, ownerId, "file.pdf", SupportedFormat.PDF, metadata, content);
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(cmd));
    }

    @Test
    void execute_rejectsBlankFilename() {
        var cmd = new UploadDocumentCommand(tenantId, ownerId, "   ", SupportedFormat.PDF, metadata, content);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("filename");
    }

    @Test
    void execute_rejectsNullFilename() {
        var cmd = new UploadDocumentCommand(tenantId, ownerId, null, SupportedFormat.PDF, metadata, content);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("filename");
    }

    @Test
    void execute_rejectsNullFormat() {
        var cmd = new UploadDocumentCommand(tenantId, ownerId, "report.pdf", null, metadata, content);
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(cmd));
    }

    @Test
    void execute_rejectsNullContent() {
        var cmd = new UploadDocumentCommand(tenantId, ownerId, "file.pdf", SupportedFormat.PDF, metadata, null);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("content");
    }

    @Test
    void execute_rejectsEmptyContent() {
        var cmd = new UploadDocumentCommand(tenantId, ownerId, "file.pdf", SupportedFormat.PDF, metadata, new byte[0]);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("content");
    }

    @Test
    void execute_rejectsFormatFilenameExtensionMismatch() {
        var cmd = new UploadDocumentCommand(tenantId, ownerId, "report.pdf", SupportedFormat.DOCX, metadata, content);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("Format mismatch");
    }

    @Test
    void execute_orchestratesFullPipelineAndPublishesEvent() {
        var cmd = new UploadDocumentCommand(
                tenantId, ownerId, "report.pdf", SupportedFormat.PDF, metadata, content);
        Document registered = Document.create(tenantId, ownerId, "report.pdf", SupportedFormat.PDF, metadata);
        Document updated    = Document.create(tenantId, ownerId, "report.pdf", SupportedFormat.PDF, metadata);
        ParsedDocument parsed = new ParsedDocument(
                "extracted text",
                new ParsedMetadata("Doc Title", null, null, 1, 13),
                SupportedFormat.PDF,
                ParsingStatus.SUCCESS,
                Instant.now());

        when(documentService.registerDocument(any(RegisterDocumentCommand.class))).thenReturn(registered);
        when(documentParser.parse(content, SupportedFormat.PDF)).thenReturn(parsed);
        when(fileStorage.store(anyString(), any(byte[].class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentService.updateDocument(any(Document.class))).thenReturn(updated);

        Document result = useCase.execute(cmd);

        assertThat(result).isSameAs(updated);
        verify(documentService).registerDocument(any(RegisterDocumentCommand.class));
        verify(documentParser).parse(content, SupportedFormat.PDF);
        verify(fileStorage, times(2)).store(anyString(), any(byte[].class));
        verify(documentService).updateDocument(any(Document.class));

        ArgumentCaptor<DocumentParsedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentParsedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("document.parsed");
        assertThat(eventCaptor.getValue().getDetectedFormat()).isEqualTo(SupportedFormat.PDF);
    }

    @Test
    void execute_acceptsUnknownExtensionWithAnyDeclaredFormat() {
        var cmd = new UploadDocumentCommand(
                tenantId, ownerId, "archive.xyz", SupportedFormat.TXT, metadata, content);
        Document registered = Document.create(tenantId, ownerId, "archive.xyz", SupportedFormat.TXT, metadata);
        Document updated    = Document.create(tenantId, ownerId, "archive.xyz", SupportedFormat.TXT, metadata);
        ParsedDocument parsed = new ParsedDocument(
                "raw text", new ParsedMetadata(null, null, null, 0, 8),
                SupportedFormat.TXT, ParsingStatus.SUCCESS, Instant.now());

        when(documentService.registerDocument(any(RegisterDocumentCommand.class))).thenReturn(registered);
        when(documentParser.parse(content, SupportedFormat.TXT)).thenReturn(parsed);
        when(fileStorage.store(anyString(), any(byte[].class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentService.updateDocument(any(Document.class))).thenReturn(updated);

        assertThat(useCase.execute(cmd)).isSameAs(updated);
        verify(documentService).updateDocument(any(Document.class));
    }
}
