package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.Charges;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargesRepository extends JpaRepository<Charges, Long> {
    Charges findChargesByChargeType(String chargeType);
}
