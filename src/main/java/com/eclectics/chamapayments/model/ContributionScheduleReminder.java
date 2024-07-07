package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tb_contribution_schedule_reminder")
public class ContributionScheduleReminder extends Auditable {
    private Long groupId;
    private String day;
    private Integer week;
    private String month;
    private String status;
    private Boolean active;
    private String contributionName;


    public ContributionScheduleReminder(Long groupId, String currentMonthString, String name, String dayOfWeek, int weekNumber) {
        setGroupId(groupId);
        setMonth(currentMonthString);
        setStatus("ACTIVE");
        setActive(true);
        setDay(dayOfWeek);
        setWeek(weekNumber);
        setContributionName(name);
    }
}
