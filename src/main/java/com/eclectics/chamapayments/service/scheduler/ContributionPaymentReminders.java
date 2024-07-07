package com.eclectics.chamapayments.service.scheduler;

import com.eclectics.chamapayments.model.Contributions;
import com.eclectics.chamapayments.model.ScheduleTypes;
import com.eclectics.chamapayments.repository.ContributionRepository;
import com.eclectics.chamapayments.repository.ScheduleTypeRepository;
import com.eclectics.chamapayments.service.ChamaKycService;
import com.eclectics.chamapayments.service.NotificationService;
import com.eclectics.chamapayments.wrappers.response.GroupWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionPaymentReminders {

    private final ScheduleTypeRepository scheduleTypeRepository;

    private final ContributionRepository contributionRepository;

    private final NotificationService notificationService;

    private final ChamaKycService chamaKycService;

    int numThreads = Runtime.getRuntime().availableProcessors();
    ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

    /**
     * Send contribution payment reminders to members of a group.
     * Cron job runs at 11 O'clock every day.
     */
    @Scheduled(cron = "0 0 11,15,22 * * *")
    void executeReminders() {
        log.info("+++++++++++++++++STARTED REMINDERS++++++++++++++++++++++++++++");
        executorService.execute(this::reminderForDailyContribution);
        executorService.execute(this::reminderForWeeklyContribution);
        executorService.execute(this::reminderForMonthlyContribution);
    }

    /**
     * Send reminder for weekly contributions.
     */

    @Transactional
    public void reminderForWeeklyContribution() {
        ScheduleTypes weeklySchedule = scheduleTypeRepository.findByNameIgnoreCaseAndSoftDeleteFalse("Weekly");
        if (!(weeklySchedule == null)) {
            List<Contributions> weeklyReminder = contributionRepository.findAllByFrequencyAndReminderAndContributionAmountGreaterThanAndActiveTrueAndSoftDeleteFalse(weeklySchedule.getName(), 1, 0);
            if (!weeklyReminder.isEmpty()) {
                weeklyReminder
                        .parallelStream()
                        .filter(contrib -> !Objects.isNull(contrib.getGroupId()) && !Objects.isNull(contrib.getDuedate()) && !Objects.isNull(contrib.getFrequency())).forEach(contribution -> {
                            LocalDate now = LocalDate.now();
                            LocalDate dueDate = contribution.getDuedate();
                            String daysDue = contribution.getDaysBeforeDue();
                            if (now.plusDays(Long.parseLong(daysDue)).equals(dueDate)) {
                                // send reminder sms
                                sendReminder(contribution, weeklySchedule);
                            }

                            if (dueDate.toString().equals(now.toString())) {
                                LocalDate updatedDueDate = dueDate.plusDays(7);
                                contribution.setDuedate(updatedDueDate);
                                contributionRepository.save(contribution);
                                log.info("UPDATE WEEKLY CONTRIBUTION {} DUE DATE TO {} FROM {}", contribution.getName(), updatedDueDate, dueDate);
                            }

                        });
            }
        }

    }

    /**
     * Send reminder for monthly contribution.
     */
    @Transactional
    public void reminderForMonthlyContribution() {
        ScheduleTypes monthlySchedule = scheduleTypeRepository.findByNameIgnoreCaseAndSoftDeleteFalse("Monthly");
        if (!(monthlySchedule == null)) {
            List<Contributions> monthlyScheduleReminder = contributionRepository.findAllByFrequencyAndReminderAndContributionAmountGreaterThanAndActiveTrueAndSoftDeleteFalse(monthlySchedule.getName(), 1, 0);
            if (!monthlyScheduleReminder.isEmpty()) {
                monthlyScheduleReminder
                        .parallelStream()
                        .filter(contrib -> !Objects.isNull(contrib.getGroupId()) && !Objects.isNull(contrib.getDuedate()) && !Objects.isNull(contrib.getFrequency())).forEach(contribution -> {
                            LocalDate now = LocalDate.now();
                            LocalDate dueDate = contribution.getDuedate();
                            String daysDue = contribution.getDaysBeforeDue();
                            if (now.plusDays(Long.parseLong(daysDue)).equals(dueDate)) {
                                sendReminder(contribution, monthlySchedule);
                            }

                            if (dueDate.toString().equals(now.toString())) {
                                LocalDate updatedDueDate = dueDate.plusMonths(1);
                                contribution.setDuedate(updatedDueDate);
                                contributionRepository.save(contribution);
                                log.info("UPDATE MONTHLY CONTRIBUTION {} DUE DATE TO {} FROM {}", contribution.getName(), updatedDueDate, dueDate);
                            }

                        });
            }
        }


    }


    /**
     * Send reminder for Daily contributions.
     */
    @Transactional
    public void reminderForDailyContribution() {
        ScheduleTypes dailySchedule = scheduleTypeRepository.findByNameIgnoreCaseAndSoftDeleteFalse("Daily");
        if (!(dailySchedule == null)) {
            List<Contributions> dailyScheduleReminder = contributionRepository.findAllByFrequencyAndReminderAndContributionAmountGreaterThanAndActiveTrueAndSoftDeleteFalse(dailySchedule.getName(), 1, 0);
            if (!dailyScheduleReminder.isEmpty()) {
                dailyScheduleReminder
                        .parallelStream()
                        .filter(contrib -> !Objects.isNull(contrib.getGroupId()) && !Objects.isNull(contrib.getDuedate()) && !Objects.isNull(contrib.getFrequency())).forEach(contribution -> {
                            LocalDate now = LocalDate.now();
                            LocalDate dueDate = contribution.getDuedate();
                            String daysDue = contribution.getDaysBeforeDue();
                            if (now.plusDays(Long.parseLong(daysDue)).equals(dueDate)) {
                                sendReminder(contribution, dailySchedule);
                            }

                            if (dueDate.toString().equals(now.toString())) {
                                LocalDate updatedDueDate = dueDate.plusMonths(1);
                                contribution.setDuedate(updatedDueDate);
                                contributionRepository.save(contribution);
                                log.info("UPDATE DAILY CONTRIBUTION {} DUE DATE TO {} FROM {}", contribution.getName(), updatedDueDate, dueDate);
                            }

                        });
            }
        }

    }

    /**
     * Publish sms events to Kafka.
     *
     * @param contribution  the contribution
     * @param scheduleTypes the schedule type
     */
    @Async
    @Transactional
    public void sendReminder(Contributions contribution, ScheduleTypes scheduleTypes) {
        GroupWrapper group = chamaKycService.getMonoGroupById(contribution.getGroupId());
        if (group != null && group.isActive()) {
            chamaKycService.getFluxGroupMembers(group.getId())
                    .subscribe(member -> notificationService.sendReminderMessage(member.getFirstname(), scheduleTypes.getName(), group.getName(), contribution.getContributionAmount(), contribution.getDuedate(), member.getPhonenumber(), member.getLanguage()));
        } else {
            log.error("COULD NOT SEND PAYMENT REMINDER SMS. GROUP NOT FOUND.");
        }
    }
}
