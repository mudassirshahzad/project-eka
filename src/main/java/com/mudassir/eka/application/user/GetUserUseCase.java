package com.mudassir.eka.application.user;

import com.mudassir.eka.domain.user.User;
import com.mudassir.eka.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserUseCase {

    private final UserApplicationService userService;

    public User execute(UserId id) {
        Objects.requireNonNull(id, "userId must not be null");
        return userService.getUser(id);
    }
}
