package com.mudassirshahzad.eka.application.document;

import com.mudassirshahzad.eka.domain.document.Document;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetDocumentUseCaseTest {

    @Mock private DocumentApplicationService documentService;
    @InjectMocks private GetDocumentUseCase useCase;

    private final DocumentId    documentId = DocumentId.generate();
    private final TenantId      tenantId   = TenantId.generate();
    private final Set<UserRole> roles      = Set.of(UserRole.USER);

    @Test
    void execute_rejectsNullDocumentId() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(null, tenantId, roles));
    }

    @Test
    void execute_rejectsNullTenantId() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(documentId, null, roles));
    }

    @Test
    void execute_rejectsNullRoles() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(documentId, tenantId, null));
    }

    @Test
    void execute_delegatesToDocumentApplicationService() {
        Document document = org.mockito.Mockito.mock(Document.class);
        when(documentService.getDocument(documentId, tenantId, roles)).thenReturn(document);

        assertThat(useCase.execute(documentId, tenantId, roles)).isSameAs(document);
    }
}
