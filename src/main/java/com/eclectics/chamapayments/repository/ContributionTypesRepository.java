package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.ContributionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * @author Alex Maina
 * @created 07/12/2021
 */
public interface ContributionTypesRepository extends JpaRepository<ContributionType, Long> {

    @Query(value = "select * from contribution_types_tbl where name='Monthly' and soft_delete=0", nativeQuery = true)
    ContributionType findContributionType();
}
