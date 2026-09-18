package com.groceryecom.modules.vendor.application;

import com.groceryecom.modules.vendor.api.dto.VendorResponse;
import com.groceryecom.modules.vendor.contract.VendorStatus;
import com.groceryecom.modules.vendor.domain.Vendor;
import com.groceryecom.modules.vendor.domain.VendorRepository;
import com.groceryecom.modules.vendor.mapper.VendorMapper;
import com.groceryecom.shared.web.PageRequestParams;
import com.groceryecom.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin's view of every store on the platform.
 *
 * <p>This is what makes approving a vendor possible at all. The approve, reject and
 * suspend endpoints each act on one store by id; without a way to find the store that is
 * waiting, an admin would have to read its id out of the database.
 *
 * <p>Separate from {@link GetVendorService} on purpose: that one answers a customer or a
 * store's own people and hides everything they may not see. This one answers an admin,
 * who may see every store in every state, including the contact details of an
 * application that was rejected. Two audiences, two services, no flag to get wrong.
 *
 * <p>Pending applications come <b>oldest first</b>: a queue is worked through in the
 * order people joined it, and the vendor who has waited longest is the one most likely to
 * give up. Everything else is newest first.
 */
@Service
public class AdminVendorService {

    private final VendorRepository vendors;

    AdminVendorService(VendorRepository vendors) {
        this.vendors = vendors;
    }

    /**
     * @param status  optional filter; null returns every store
     * @param search  optional match on the store's display name or storefront address
     */
    @Transactional(readOnly = true)
    public PageResponse<VendorResponse> list(VendorStatus status, String search, int page, int size) {
        Pageable pageable = status == VendorStatus.PENDING
                ? PageRequestParams.oldestFirst(page, size)
                : PageRequestParams.newestFirst(page, size);

// Empty, never null: PostgreSQL cannot type a null inside CONCAT
        String term = search == null ? "" : search.trim();
        Page<Vendor> found = vendors.search(status, term, pageable);

        return PageResponse.of(found, VendorMapper::toResponse);
    }
}
