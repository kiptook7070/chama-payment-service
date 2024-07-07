package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.ContributionScheduleReminder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContributionScheduleReminderRepository extends JpaRepository<ContributionScheduleReminder, Long> {
    ContributionScheduleReminder findTopByGroupIdAndMonthAndActiveTrueOrderByIdDesc(Long groupId, String month);

    ContributionScheduleReminder findTopByGroupIdAndWeekAndActiveTrueOrderByIdDesc(Long groupId, Integer week);

    ContributionScheduleReminder findTopByGroupIdAndWeekAndDayAndActiveTrueOrderByIdDesc(Long groupId, Integer week, String day);
}
