package com.groceryecom.modules.billing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentGatewayCredentialRepository extends JpaRepository<PaymentGatewayCredential, Long> {

    Optional<PaymentGatewayCredential> findByProvider(String provider);

    List<PaymentGatewayCredential> findAllByOrderByProviderAsc();
}
