package com.groceryecom.modules.billing.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Where a store wants its share of customers' payments sent.
 *
 * <p>The account number and IFSC code are passed to the payment provider and are
 * <b>never stored</b> by this application - only the provider's id for the destination
 * and the last four digits are kept. They are also masked in the request log, which is
 * why the fields are named the way they are ({@code SensitiveData}).
 *
 * @param accountNumber the vendor's bank account number
 * @param ifscCode      the branch code, upper case
 * @param contactEmail  where the provider sends verification questions
 */
public record SavePayoutAccountRequest(
        @NotBlank @Size(min = 2, max = 160)
        String accountHolderName,

        @NotBlank @Size(min = 6, max = 34)
        @Pattern(regexp = "^[0-9]+$", message = "must be digits only")
        String accountNumber,

        @NotBlank @Size(min = 4, max = 20)
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "must be letters and digits only")
        String ifscCode,

        @Size(max = 120)
        String bankName,

        @NotBlank @Email @Size(max = 255)
        String contactEmail) {
}
