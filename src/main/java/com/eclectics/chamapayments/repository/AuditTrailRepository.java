package com.eclectics.chamapayments.repository;


import com.eclectics.chamapayments.model.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long> {

}
