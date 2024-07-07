package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.Parameters;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametersRepository extends JpaRepository<Parameters, Long> {
    Parameters findTopByNameIgnoreCaseAndSoftDeleteIsFalse(String name);
}
