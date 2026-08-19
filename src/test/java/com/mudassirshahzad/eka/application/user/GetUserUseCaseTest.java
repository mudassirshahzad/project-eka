package com.mudassirshahzad.eka.application.user;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.User;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserUseCaseTest {

    @Mock  private UserApplicationService userService;
    @InjectMocks private GetUserUseCase useCase;

    private final UserId   userId   = UserId.generate();
    private final TenantId tenantId = TenantId.generate();

    @Test
    void execute_rejectsNullUserId() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(null, tenantId));
    }

    @Test
    void execute_rejectsNullTenantId() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(userId, null));
    }

    @Test
    void execute_delegatesToService() {
        User user = User.create(tenantId, "user@example.com", "hashed", EnumSet.of(UserRole.USER));
        when(userService.getUser(userId, tenantId)).thenReturn(user);

        User result = useCase.execute(userId, tenantId);

        assertThat(result).isSameAs(user);
        verify(userService).getUser(userId, tenantId);
    }
}
