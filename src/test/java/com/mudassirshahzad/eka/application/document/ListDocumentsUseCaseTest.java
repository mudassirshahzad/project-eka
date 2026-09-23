package com.mudassirshahzad.eka.application.document;

import com.mudassirshahzad.eka.domain.document.Document;
import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListDocumentsUseCaseTest {

    @Mock private DocumentApplicationService documentService;
    @InjectMocks private ListDocumentsUseCase useCase;

    private final TenantId      tenantId    = TenantId.generate();
    private final UserId        ownerId     = UserId.generate();
    private final PageRequest   pageRequest = PageRequest.first(20);
    private final Set<UserRole> roles       = Set.of(UserRole.USER);

    @Test
    void execute_rejectsNullTenantId() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(null, pageRequest, roles));
    }

    @Test
    void execute_rejectsNullPageRequest() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(tenantId, null, roles));
    }

    @Test
    void execute_rejectsNullRoles() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(tenantId, pageRequest, null));
    }

    @Test
    void execute_delegatesToDocumentApplicationService() {
        PageResult<Document> page = PageResult.of(List.of(), 0, 20, 0);
        when(documentService.listDocuments(tenantId, pageRequest, roles)).thenReturn(page);

        assertThat(useCase.execute(tenantId, pageRequest, roles)).isSameAs(page);
    }

    @Test
    void executeByOwner_rejectsNullRoles() {
        assertThatNullPointerException()
                .isThrownBy(() -> useCase.executeByOwner(ownerId, tenantId, pageRequest, null));
    }

    @Test
    void executeByOwner_delegatesToDocumentApplicationService() {
        PageResult<Document> page = PageResult.of(List.of(), 0, 20, 0);
        when(documentService.listDocumentsByOwner(ownerId, tenantId, pageRequest, roles)).thenReturn(page);

        assertThat(useCase.executeByOwner(ownerId, tenantId, pageRequest, roles)).isSameAs(page);
    }
}
