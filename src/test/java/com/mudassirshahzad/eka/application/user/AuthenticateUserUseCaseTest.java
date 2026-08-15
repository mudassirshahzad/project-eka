package com.mudassirshahzad.eka.application.user;

import com.mudassirshahzad.eka.application.shared.InvalidCredentialsException;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.User;
import com.mudassirshahzad.eka.domain.user.UserRepository;
import com.mudassirshahzad.eka.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AuthenticateUserUseCase useCase;

    private final TenantId tenantId = TenantId.generate();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        useCase = new AuthenticateUserUseCase(userRepository, passwordEncoder);
    }

    @Test
    void execute_validCredentials_returnsUser() {
        User user = User.create(tenantId, "user@example.com", "hashed", EnumSet.of(UserRole.USER));
        when(userRepository.findByEmailAndTenantId("user@example.com", tenantId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

        User result = useCase.execute(new AuthenticateUserCommand("user@example.com", "password", tenantId));

        assertThat(result).isEqualTo(user);
    }

    @Test
    void execute_unknownEmail_throwsInvalidCredentials() {
        when(userRepository.findByEmailAndTenantId(anyString(), eq(tenantId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new AuthenticateUserCommand("nobody@example.com", "password", tenantId)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void execute_wrongPassword_throwsInvalidCredentials() {
        User user = User.create(tenantId, "user@example.com", "hashed", EnumSet.of(UserRole.USER));
        when(userRepository.findByEmailAndTenantId("user@example.com", tenantId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new AuthenticateUserCommand("user@example.com", "wrong", tenantId)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void execute_inactiveUser_throwsInvalidCredentialsEvenWithCorrectPassword() {
        User user = User.create(tenantId, "user@example.com", "hashed", EnumSet.of(UserRole.USER));
        user.deactivate();
        when(userRepository.findByEmailAndTenantId("user@example.com", tenantId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(new AuthenticateUserCommand("user@example.com", "password", tenantId)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void execute_unknownEmail_stillInvokesPasswordEncoder_toAvoidTimingLeak() {
        when(userRepository.findByEmailAndTenantId("nobody@example.com", tenantId)).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("password"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new AuthenticateUserCommand("nobody@example.com", "password", tenantId)))
                .isInstanceOf(InvalidCredentialsException.class);

        org.mockito.Mockito.verify(passwordEncoder).matches(eq("password"), anyString());
    }

    @Test
    void execute_unknownEmailAndWrongPassword_throwSameExceptionType() {
        when(userRepository.findByEmailAndTenantId(anyString(), any())).thenReturn(Optional.empty());

        InvalidCredentialsException unknownUserEx = catchInvalidCredentials(
                () -> useCase.execute(new AuthenticateUserCommand("nobody@example.com", "x", tenantId)));

        assertThat(unknownUserEx.getMessage()).isEqualTo("Invalid email, password, or tenant");
    }

    private InvalidCredentialsException catchInvalidCredentials(Runnable action) {
        try {
            action.run();
        } catch (InvalidCredentialsException ex) {
            return ex;
        }
        throw new AssertionError("expected InvalidCredentialsException");
    }
}
