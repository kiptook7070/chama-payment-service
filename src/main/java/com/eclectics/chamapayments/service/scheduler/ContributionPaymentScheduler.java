package com.eclectics.chamapayments.service.scheduler;

import com.eclectics.chamapayments.model.ContributionSchedulePayment;
import com.eclectics.chamapayments.model.ContributionScheduleReminder;
import com.eclectics.chamapayments.model.Contributions;
import com.eclectics.chamapayments.model.ScheduleTypes;
import com.eclectics.chamapayments.repository.ContributionRepository;
import com.eclectics.chamapayments.repository.ContributionSchedulePaymentRepository;
import com.eclectics.chamapayments.repository.ContributionScheduleReminderRepository;
import com.eclectics.chamapayments.repository.ScheduleTypeRepository;
import com.eclectics.chamapayments.service.ChamaKycService;
import com.eclectics.chamapayments.service.NotificationService;
import com.eclectics.chamapayments.wrappers.response.GroupWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Handles the creation of upcoming contributions for every group and its contributions.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ContributionPaymentScheduler {

    private final ChamaKycService chamaKycService;
    private final ScheduleTypeRepository scheduleTypeRepository;
    private final ContributionRepository contributionRepository;
    private final NotificationService notificationService;
    private final ContributionSchedulePaymentRepository contributionSchedulePaymentRepository;
    private final ContributionScheduleReminderRepository contributionScheduleReminderRepository;

    int numThreads = Runtime.getRuntime().availableProcessors();
    ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
    ScheduledExecutorService asyncOperationEmulation;


    /**
     * Scheduler to update daily upcoming contributions.
     */
    @Scheduled(cron = "0 8 * * * *")
    @Transactional
    public void scheduleDailyPayment() {
        ScheduleTypes dailySchedule = scheduleTypeRepository.findByNameIgnoreCaseAndSoftDeleteFalse("Daily");
        if (!(dailySchedule == null)) {
            List<Contributions> weeklyContributions = contributionRepository.findAllByScheduleTypeAndReminderAndContributionAmountGreaterThanAndActiveTrueAndSoftDeleteFalse(dailySchedule, 1, 0);
            if (!weeklyContributions.isEmpty()) {
                weeklyContributions
                        .parallelStream()
                        .filter(contrib -> !Objects.isNull(contrib.getGroupId()) && !Objects.isNull(contrib.getFrequency())).forEach(cont -> {
                            LocalDate currentDate = LocalDate.now();
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM");
                            String currentMonthString = currentDate.format(formatter);
                            String dayOfWeek = String.valueOf(currentDate.getDayOfWeek());

                            Locale locale = Locale.getDefault();
                            WeekFields weekFields = WeekFields.of(locale);
                            // Get the week number
                            int weekNumber = currentDate.get(weekFields.weekOfWeekBasedYear());


                            ContributionScheduleReminder dailyReminder = contributionScheduleReminderRepository.findTopByGroupIdAndWeekAndDayAndActiveTrueOrderByIdDesc(cont.getGroupId(), weekNumber, dayOfWeek);
                            if (dailyReminder == null) {
                                ContributionScheduleReminder contributionScheduleReminder = new ContributionScheduleReminder(cont.getGroupId(), currentMonthString, cont.getName(), dayOfWeek, weekNumber);
                                contributionScheduleReminderRepository.save(contributionScheduleReminder);
                                //TODO:: SEND NOTIFICATION
                                sendMonthlyContributionReminder(cont.getGroupId(), cont.getContributionAmount(), currentMonthString, cont.getFrequency());
                                Date date = getMonthlyDueDate(cont.getDuedate());
                                String paymentScheduleId = generateMonthlyPaymentScheduleId(date, cont);
                                saveContributionSchedulePayment(cont, date, paymentScheduleId);
                            }
                        });
            }
        }
    }

    /**
     * Schedule upcoming weekly payments.
     */
    @Scheduled(cron = "0 11 * * * *")
    @Transactional
    public void scheduleWeeklyPayment() {
        ScheduleTypes scheduleType = scheduleTypeRepository.findByNameIgnoreCaseAndSoftDeleteFalse("Weekly");
        if (!(scheduleType == null)) {
            List<Contributions> weeklyContributions = contributionRepository.findAllByScheduleTypeAndReminderAndContributionAmountGreaterThanAndActiveTrueAndSoftDeleteFalse(scheduleType, 1, 0);
            if (!weeklyContributions.isEmpty()) {
                weeklyContributions
                        .parallelStream()
                        .filter(contrib -> !Objects.isNull(contrib.getGroupId()) && !Objects.isNull(contrib.getFrequency())).forEach(cont -> {

                            LocalDate currentDate = LocalDate.now();
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM");
                            String currentMonthString = currentDate.format(formatter);
                            String dayOfWeek = String.valueOf(currentDate.getDayOfWeek());

                            Locale locale = Locale.getDefault();
                            WeekFields weekFields = WeekFields.of(locale);
                            // Get the week number
                            int weekNumber = currentDate.get(weekFields.weekOfWeekBasedYear());


                            ContributionScheduleReminder weeklyReminder = contributionScheduleReminderRepository.findTopByGroupIdAndWeekAndActiveTrueOrderByIdDesc(cont.getGroupId(), weekNumber);
                            if (weeklyReminder == null) {
                                ContributionScheduleReminder contributionScheduleReminder = new ContributionScheduleReminder(cont.getGroupId(), currentMonthString, cont.getName(), dayOfWeek, weekNumber);
                                contributionScheduleReminderRepository.save(contributionScheduleReminder);
                                //TODO:: SEND NOTIFICATION
                                sendMonthlyContributionReminder(cont.getGroupId(), cont.getContributionAmount(), currentMonthString, cont.getFrequency());
                                Date date = getMonthlyDueDate(cont.getDuedate());
                                String paymentScheduleId = generateMonthlyPaymentScheduleId(date, cont);
                                saveContributionSchedulePayment(cont, date, paymentScheduleId);
                            }
                        });
            }

        }
    }


    /**
     * Scheduler to update monthly upcoming contributions.
     */
    @Scheduled(cron = "0 0 11,15,22 * * *")
    @Transactional
    public void scheduleMonthlyPayment() {
        ScheduleTypes scheduleType = scheduleTypeRepository.findByNameIgnoreCaseAndSoftDeleteFalse("Monthly");
        if (!(scheduleType == null)) {
            List<Contributions> monthlyContributions = contributionRepository.findAllByScheduleTypeAndReminderAndContributionAmountGreaterThanAndActiveTrueAndSoftDeleteFalse(scheduleType, 1, 0);
            if (!monthlyContributions.isEmpty()) {
                monthlyContributions
                        .parallelStream()
                        .filter(monthContrib -> !Objects.isNull(monthContrib.getGroupId()) && !Objects.isNull(monthContrib.getFrequency())).forEach(cont -> {
                            LocalDate currentDate = LocalDate.now();
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM");
                            String currentMonthString = currentDate.format(formatter);
                            String dayOfWeek = String.valueOf(currentDate.getDayOfWeek());
                            Locale locale = Locale.getDefault();
                            WeekFields weekFields = WeekFields.of(locale);
                            // Get the week number
                            int weekNumber = currentDate.get(weekFields.weekOfWeekBasedYear());

                            ContributionScheduleReminder reminder = contributionScheduleReminderRepository.findTopByGroupIdAndMonthAndActiveTrueOrderByIdDesc(cont.getGroupId(), currentMonthString);
                            if (reminder == null) {
                                ContributionScheduleReminder contributionScheduleReminder = new ContributionScheduleReminder(cont.getGroupId(), currentMonthString, cont.getName(), dayOfWeek, weekNumber);
                                contributionScheduleReminderRepository.save(contributionScheduleReminder);
                                //TODO:: SEND NOTIFICATION
                                sendMonthlyContributionReminder(cont.getGroupId(), cont.getContributionAmount(), currentMonthString, cont.getFrequency());
                                Date date = getMonthlyDueDate(cont.getDuedate());
                                String paymentScheduleId = generateMonthlyPaymentScheduleId(date, cont);
                                saveContributionSchedulePayment(cont, date, paymentScheduleId);
                            }
                        });
            }

        } else {
            log.info("Schedule type not found... On monthly payment scheduling.");
        }
    }


    private void sendMonthlyContributionReminder(Long groupId, double contributionAmount, String currentMonthString, String frequency) {
        GroupWrapper group = chamaKycService.getMonoGroupById(groupId);
        if (group != null && group.isActive()) {
            chamaKycService.getFluxGroupMembers(group.getId())
                    .subscribe(member -> notificationService.sendMonthlyContributionReminder(member.getFirstname(), group.getName(), contributionAmount, member.getPhonenumber(), currentMonthString, frequency, member.getLanguage()));
        } else {
            log.error("COULD NOT CONTRIBUTION SMS. GROUP NOT FOUND.");
        }
    }

    /**
     * Generate unique id for monthly scheduled payment.
     *
     * @param date         the monthly due date
     * @param contribution the contribution entity
     * @return a unique id
     */
    private String generateMonthlyPaymentScheduleId(Date date, Contributions contribution) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int month = calendar.get(Calendar.MONTH);
        int year = calendar.get(Calendar.YEAR);

        return "CHAM" + contribution.getId() + year + month;
    }

    /**
     * Get the updated monthly due date
     *
     * @param dueDate the due date
     * @return an updated monthly date
     */
    private Date getMonthlyDueDate(LocalDate dueDate) {
        if (dueDate == null)
            return new Date();
        Calendar calendar = Calendar.getInstance();

        int currentDate = calendar.get(Calendar.DAY_OF_MONTH);
        if (currentDate == dueDate.getDayOfMonth()) {
            return new Date();
        }

        if (dueDate.getDayOfMonth() <= currentDate) {
            calendar.set(Calendar.MONTH, calendar.get(Calendar.MONTH) + 1);
        }
        calendar.set(Calendar.DAY_OF_MONTH, dueDate.getDayOfMonth());
        return calendar.getTime();
    }

    /**
     * Saves a contribution scheduled payment.
     *
     * @param contribution      the contribution
     * @param date              the due date
     * @param paymentScheduleId the unique id of the scheduled payment
     */
    private void saveContributionSchedulePayment(Contributions contribution, Date date, String paymentScheduleId) {
        SimpleDateFormat dateFor = new SimpleDateFormat("dd/MM/yyyy");
        String stringDate = dateFor.format(date);

        int count = contributionSchedulePaymentRepository.countByContributionScheduledId(paymentScheduleId);
        log.info("Scheduled Contributions count type... {} {}", count, paymentScheduleId);

        if (count == 0) {
            // make sure there is no duplicates for scheduled payments
            ContributionSchedulePayment contributionSchedulePayment = new ContributionSchedulePayment();

            contributionSchedulePayment.setContributionId(contribution.getId());
            contributionSchedulePayment.setContributionScheduledId(paymentScheduleId);
            contributionSchedulePayment.setExpectedContributionDate(stringDate);
            contributionSchedulePaymentRepository.save(contributionSchedulePayment);
        }
    }

    /**
     * Generate unique id for daily scheduled payment.
     *
     * @param contribution the contribution entity
     * @return a unique id
     */
    private String generateDailyPaymentScheduleId(Contributions contribution) {
        Calendar date = Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);

        date.add(Calendar.DAY_OF_MONTH, 1);

        int dayOfTheYear = date.get(Calendar.DAY_OF_YEAR);
        int year = date.get(Calendar.YEAR);
        return "CHAD" + contribution.getId() + dayOfTheYear + year;
    }

    /**
     * Get the daily due date
     *
     * @return a date
     */
    private Date getDailyDueDate() {
        Calendar date = Calendar.getInstance();
        date.setTime(new Date());
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        date.add(Calendar.DAY_OF_MONTH, 1);
        return date.getTime();
    }
}
