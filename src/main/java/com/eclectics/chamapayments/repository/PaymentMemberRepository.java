package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentMemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByImsi(String username);

    Member findFirstByImsiIsAndSoftDeleteFalse(String imsi);
}
