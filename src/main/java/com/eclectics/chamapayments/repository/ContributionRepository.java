package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.Contributions;
import com.eclectics.chamapayments.model.ScheduleTypes;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * @author Alex Maina
 * @created 07/12/2021
 */
public interface ContributionRepository extends JpaRepository<Contributions, Long> {
    int countByNameAndMemberGroupIdAndSoftDeleteFalse(String name, long groups);

    Optional<Contributions> findByIdAndSoftDeleteFalse(long id);

    List<Contributions> findByMemberGroupIdAndSoftDeleteFalse(long groupId, Pageable pageable);

    Optional<Contributions> findByMemberGroupIdAndSoftDeleteFalse(long groupId);

    int countByActiveTrueAndSoftDeleteFalse();

    int countByActiveFalseAndSoftDeleteFalse();

    List<Contributions> findAllByMemberGroupIdAndSoftDeleteFalse(long groupId);

    List<Contributions> findAllByScheduleTypeAndReminderAndContributionAmountGreaterThanAndActiveTrueAndSoftDeleteFalse(ScheduleTypes scheduleType, Integer reminder, double contributionAmount);
    List<Contributions> findAllByFrequencyAndReminderAndContributionAmountGreaterThanAndActiveTrueAndSoftDeleteFalse(String frequency, Integer reminder, double contributionAmount);

    Optional<Contributions> findByIdAndMemberGroupIdAndSoftDeleteFalse(Long groupId, Long memberGroupId);

    Optional<Contributions> findByGroupIdAndActiveTrueAndSoftDeleteFalse(long groupid);

    Contributions findFirstByGroupIdAndActiveTrueAndSoftDeleteFalse(long groupId);

    List<Contributions> findAllBySoftDeleteFalseOrderByGroupIdAsc();

}
