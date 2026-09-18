package com.groceryecom.platform.web;

import com.groceryecom.shared.exception.ConflictException;
import com.groceryecom.shared.exception.NotFoundException;
import com.groceryecom.shared.exception.UnauthorizedException;
import com.groceryecom.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The error contract, exception by exception. Clients switch on {@code code}, so a
 * change here is a breaking API change.
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void validationExceptionBecomes400WithItsCode() throws Exception {
        mockMvc.perform(get("/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INCORRECT_PASSWORD"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unauthorizedExceptionBecomes401() throws Exception {
        mockMvc.perform(get("/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void notFoundExceptionBecomes404AndDoesNotEchoTheId() throws Exception {
        mockMvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void conflictExceptionBecomes409() throws Exception {
        mockMvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
    }

    /** Two writers hit the same row; the caller should reload and retry, so 409 not 500. */
    @Test
    void optimisticLockFailureBecomes409ConcurrentModification() throws Exception {
        mockMvc.perform(get("/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void anUnexpectedExceptionBecomes500WithNoInternalDetail() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/validation")
        void validation() {
            throw new ValidationException("Old password is incorrect", "INCORRECT_PASSWORD");
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException("Invalid username or password");
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("User", UUID.randomUUID());
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("Username already exists", "USERNAME_EXISTS");
        }

        @GetMapping("/stale")
        void stale() {
            throw new ObjectOptimisticLockingFailureException("users", 1L);
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("connection pool exhausted at 10.0.0.5:5432");
        }
    }
}
