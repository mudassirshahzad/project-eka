package com.mudassir.eka.application.user;

import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.User;
import com.mudassir.eka.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock  private UserApplicationService userService;
    @InjectMocks private RegisterUserUseCase useCase;

    private final TenantId      tenantId     = TenantId.generate();
    private final Set<UserRole> defaultRoles = Set.of(UserRole.USER);

    @Test
    void execute_rejectsNullCommand() {
        assertThatNullPointerException().isThrownBy(() -> useCase.execute(null));
    }

    @Test
    void execute_rejectsNullEmail() {
        var cmd = new RegisterUserCommand(tenantId, null, "hash", defaultRoles);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("email");
    }

    @Test
    void execute_rejectsBlankEmail() {
        var cmd = new RegisterUserCommand(tenantId, "  ", "hash", defaultRoles);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("email");
    }

    @Test
    void execute_rejectsEmailWithNoAtSign() {
        var cmd = new RegisterUserCommand(tenantId, "userexample.com", "hash", defaultRoles);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("@");
    }

    @Test
    void execute_rejectsEmailWithAtSignAtStart() {
        var cmd = new RegisterUserCommand(tenantId, "@example.com", "hash", defaultRoles);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("@");
    }

    @Test
    void execute_rejectsEmailWithAtSignAtEnd() {
        var cmd = new RegisterUserCommand(tenantId, "user@", "hash", defaultRoles);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("@");
    }

    @Test
    void execute_rejectsBlankPasswordHash() {
        var cmd = new RegisterUserCommand(tenantId, "user@example.com", "  ", defaultRoles);
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("passwordHash");
    }

    @Test
    void execute_rejectsEmptyRoles() {
        var cmd = new RegisterUserCommand(tenantId, "user@example.com", "hash", Set.of());
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> useCase.execute(cmd))
                .withMessageContaining("role");
    }

    @Test
    void execute_delegatesWhenValid() {
        var cmd = new RegisterUserCommand(tenantId, "user@example.com", "hash", defaultRoles);
        User saved = User.create(tenantId, "user@example.com", "hash", defaultRoles);
        when(userService.registerUser(cmd)).thenReturn(saved);

        User result = useCase.execute(cmd);

        assertThat(result).isSameAs(saved);
        verify(userService).registerUser(cmd);
    }
}
