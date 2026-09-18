package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.UserResponse;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.modules.identity.mapper.UserMapper;
import com.groceryecom.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reads one user's profile by public ID.
 */
@Service
public class GetUserService {

    private final UserRepository userRepository;

    GetUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse execute(UUID userId) {
        return userRepository.findByPublicId(userId)
                .map(UserMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("User", userId));
    }
}
