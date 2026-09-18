package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.UserResponse;
import com.groceryecom.modules.identity.contract.Role;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.modules.identity.mapper.UserMapper;
import com.groceryecom.shared.web.PageRequestParams;
import com.groceryecom.shared.web.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lookup for the admin console.
 *
 * <p>A support call starts with "my login stopped working" and a name read out over the
 * phone, so this searches the username and the email. It returns the same
 * {@link UserResponse} the account's owner sees - an admin needs to find an account and
 * check its state, not read anything the person themselves cannot see.
 *
 * <p>There is no password, no hash and no token in that response, and there is no
 * endpoint here that changes an account. Read-only, on purpose: an admin action that
 * alters somebody's account is a separate decision with its own audit line.
 */
@Service
public class AdminUserService {

    private final UserRepository users;

    AdminUserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(Role role, String search, int page, int size) {
// Empty, never null: PostgreSQL cannot type a null inside CONCAT
        String term = search == null ? "" : search.trim();

        return PageResponse.of(
                users.search(role, term, PageRequestParams.newestFirst(page, size)),
                UserMapper::toResponse);
    }
}
