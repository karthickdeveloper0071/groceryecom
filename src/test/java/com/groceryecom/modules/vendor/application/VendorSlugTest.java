package com.groceryecom.modules.vendor.application;

import com.groceryecom.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The storefront address ends up in a host name and in every URL for that store, so
 * what is accepted here is a decision that cannot be taken back later.
 */
class VendorSlugTest {

    @Test
    void trimsAndLowerCasesSoTheSameStoreIsNotRegisteredTwice() {
        assertThat(VendorSlug.normalize("  Fresh-Mart  ")).isEqualTo("fresh-mart");
    }

    @Test
    void collapsesRepeatedHyphens() {
        assertThat(VendorSlug.normalize("fresh--mart")).isEqualTo("fresh-mart");
    }

    @Test
    void rejectsSomethingThatIsNotAValidHostLabel() {
        assertThatThrownBy(() -> VendorSlug.normalize("fresh mart"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> VendorSlug.normalize("-freshmart"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> VendorSlug.normalize("fresh.mart"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsSomethingTooShortToBeADistinctAddress() {
        assertThatThrownBy(() -> VendorSlug.normalize("ab"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("3-63");
    }

    /** A vendor taking admin.groceryecom.com would be handed our own traffic. */
    @Test
    void rejectsAddressesThePlatformUsesItself() {
        assertThatThrownBy(() -> VendorSlug.normalize("Admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reserved");
    }
}
