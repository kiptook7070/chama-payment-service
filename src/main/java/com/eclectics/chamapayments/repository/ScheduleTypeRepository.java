package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.ScheduleTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * @author Alex Maina
 * @created 07/12/2021
 */
public interface ScheduleTypeRepository extends JpaRepository<ScheduleTypes, Long> {

    ScheduleTypes findByNameIgnoreCaseAndSoftDeleteFalse(String name);

}
