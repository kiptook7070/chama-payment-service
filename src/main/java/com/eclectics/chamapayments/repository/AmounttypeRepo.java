package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.AmountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;


public interface AmounttypeRepo extends JpaRepository<AmountType, Long> {

    @Query(value = "select * from amount_types where name='FLEXIBLE-AMOUNT' and soft_delete=0", nativeQuery = true)
    AmountType findAmountName();

}
