package com.groceryecom.modules.identity.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Objects;

public record ChangePasswordRequest(
        @NotBlank String oldPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword,
        @NotBlank String confirmPassword) {

    @JsonIgnore
    @AssertTrue(message = "must match newPassword")
    public boolean isConfirmPasswordMatching() {
        return Objects.equals(newPassword, confirmPassword);
    }
}
