package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.LoanProducts;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;


public interface LoanproductsRepository extends JpaRepository<LoanProducts, Long> {
    int countByGroupIdAndProductname(long groupId, String productname);

    List<LoanProducts> findAllByGroupIdAndSoftDeleteFalse(@Param("groupId") long groupId);

    LoanProducts findFirstByGroupIdAndIsActiveTrueAndSoftDeleteFalse(long groupId);

    LoanProducts findTopByGroupIdAndIsActiveTrueAndSoftDeleteFalseOrderByIdDesc(long groupId);

    List<LoanProducts> findAllByIsActiveAndSoftDeleteAndCreatedOnBetween(boolean isActive, boolean softDelete, Date startDate, Date endDate, Pageable pageable);

    int countLoanProductsByIsActiveAndSoftDeleteAndCreatedOnBetween(boolean isActive, boolean softDelete, Date startDate, Date endDate);

    List<LoanProducts> findAllBySoftDeleteAndCreatedOnBetween(boolean softDelete, Date startDate, Date endDate, Pageable pageable);

    int countAllBySoftDeleteAndCreatedOnBetween(boolean softDelete, Date startDate, Date endDate);

    List<LoanProducts> findAllByGroupIdAndSoftDelete(long groupId, boolean softDelete, Pageable pageable);

    int countAllByGroupIdAndSoftDelete(long groupId, boolean softDelete);

    int countByGroupIdAndIsActiveAndCreatedOnBetween(long groupId, boolean isActive, Date startDate, Date endDate);

    List<LoanProducts> findAllByGroupIdAndIsActiveAndCreatedOnBetween(long groupId, boolean b, Date startDate, Date endDate, Pageable pageable);

    List<LoanProducts> findAllByGroupIdAndIsActive(Long groupId, boolean isActive);

    List<LoanProducts> findAllBySoftDeleteFalseOrderByGroupIdAsc();
}
