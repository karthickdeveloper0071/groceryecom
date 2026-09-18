package com.groceryecom.modules.vendor.application;

import com.groceryecom.modules.vendor.api.dto.RegisterVendorRequest;
import com.groceryecom.modules.vendor.api.dto.VendorResponse;
import com.groceryecom.modules.vendor.contract.VendorMemberRole;
import com.groceryecom.modules.vendor.contract.VendorRegisteredEvent;
import com.groceryecom.modules.vendor.contract.VendorStatus;
import com.groceryecom.modules.vendor.domain.VendorMember;
import com.groceryecom.modules.vendor.domain.VendorMemberRepository;
import com.groceryecom.modules.vendor.domain.VendorRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Applying to open a store.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisterVendorServiceTest {

    private static final UUID APPLICANT = UUID.randomUUID();

    @Mock
    private VendorRepository vendors;

    @Mock
    private VendorMemberRepository members;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private AuditLog auditLog;

    @InjectMocks
    private RegisterVendorService registerVendorService;

    @Test
    void aNewStoreStartsPendingWithTheApplicantAsItsOwner() {
        givenNoExistingMembership();
        when(vendors.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        VendorResponse response = registerVendorService.execute(applicant(), request("Fresh-Mart"));

        assertThat(response.status()).isEqualTo(VendorStatus.PENDING);
        // Not the raw input: the address is normalised before anything is stored
        assertThat(response.slug()).isEqualTo("fresh-mart");

        ArgumentCaptor<VendorMember> membership = ArgumentCaptor.forClass(VendorMember.class);
        verify(members).save(membership.capture());
        assertThat(membership.getValue().getUserPublicId()).isEqualTo(APPLICANT);
        assertThat(membership.getValue().getMemberRole()).isEqualTo(VendorMemberRole.OWNER);

        verify(events).publishEvent(any(VendorRegisteredEvent.class));
        verify(auditLog).vendorRegistered(eq(APPLICANT), any());
    }

    @Test
    void anAccountThatAlreadyBelongsToAStoreCannotOpenAnother() {
        when(members.existsByUserPublicId(APPLICANT)).thenReturn(true);

        assertThatThrownBy(() -> registerVendorService.execute(applicant(), request("second-store")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already belongs to a store");

        verify(vendors, never()).saveAndFlush(any());
    }

    @Test
    void aTakenStoreAddressIsRefused() {
        givenNoExistingMembership();
        when(vendors.existsBySlug("fresh-mart")).thenReturn(true);

        assertThatThrownBy(() -> registerVendorService.execute(applicant(), request("fresh-mart")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already taken");

        verify(vendors, never()).saveAndFlush(any());
    }

    /**
     * Two applications for the same address can both pass the check before either
     * inserts. The unique constraint decides, and the loser gets the same 409 rather
     * than a 500.
     */
    @Test
    void losingTheRaceForAnAddressReadsTheSameAsLosingItEarlier() {
        givenNoExistingMembership();
        when(vendors.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_vendors_slug"));

        assertThatThrownBy(() -> registerVendorService.execute(applicant(), request("fresh-mart")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already taken");

        verify(members, never()).save(any());
    }

    private void givenNoExistingMembership() {
        when(members.existsByUserPublicId(APPLICANT)).thenReturn(false);
        when(vendors.existsBySlug(any())).thenReturn(false);
    }

    private static AuthenticatedUser applicant() {
        return new AuthenticatedUser(APPLICANT, "asha", Set.of("CUSTOMER"), "token-id",
                UUID.randomUUID(), Instant.now());
    }

    private static RegisterVendorRequest request(String slug) {
        return new RegisterVendorRequest(slug, "Fresh Mart Sdn Bhd", "Fresh Mart",
                "Owner@FreshMart.example", "+60123456789");
    }
}
