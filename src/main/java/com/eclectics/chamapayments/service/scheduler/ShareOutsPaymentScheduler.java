package com.eclectics.chamapayments.service.scheduler;

import com.eclectics.chamapayments.model.ContributionPayment;
import com.eclectics.chamapayments.model.ShareOuts;
import com.eclectics.chamapayments.model.ShareOutsPayment;
import com.eclectics.chamapayments.repository.ContributionsPaymentRepository;
import com.eclectics.chamapayments.repository.ShareOutsPaymentRepository;
import com.eclectics.chamapayments.repository.ShareOutsRepository;
import com.eclectics.chamapayments.service.enums.PaymentEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ShareOutsPaymentScheduler {
    private final ShareOutsRepository shareOutsRepository;
    private final ShareOutsPaymentRepository shareOutsPaymentRepository;
    private final ContributionsPaymentRepository paymentRepository;

    @Scheduled(fixedDelay = 60000)
    public void monthlyShareOut() {
        List<ContributionPayment> checkPayments = paymentRepository.findContributionsPayments();
        if (!checkPayments.isEmpty()) {
            for (ContributionPayment payment :
                    checkPayments) {
                LocalDate currentDate = LocalDate.now();
                LocalDate localDate = payment.getLastModifiedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                // If you want to format the month as a string (e.g., "January", "February")
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM");
                String currentMonthString = currentDate.format(formatter);
                Locale locale = Locale.getDefault();
                WeekFields weekFields = WeekFields.of(locale);
                // Get the week number
                int weekNumber = currentDate.get(weekFields.weekOfWeekBasedYear());

                ShareOuts checkCurrentShareOut = shareOutsRepository.findFirstByGroupIdAndPhoneNumberAndMonthAndPaymentStatusAndSoftDeleteFalse(payment.getGroupId(), payment.getPhoneNumber(), currentMonthString.toLowerCase(), payment.getPaymentStatus());
                if (checkCurrentShareOut == null) {
                    log.info("SHARE OUT NOT FOUND ==== NEW CONTRIBUTION AMOUNT {} FOR {} IN CASE OF CORE ACCOUNT {}", payment.getAmount(), payment.getPhoneNumber(), payment.getCoreAccount());
                    ShareOuts shareOuts = new ShareOuts(payment.getGroupId(), payment.getContributionId(), payment.getId(), payment.getPhoneNumber(), payment.getCoreAccount(), payment.getPaymentStatus(), currentDate.getYear(), payment.getAmount(), currentDate.getMonth(), currentMonthString.toLowerCase(), weekNumber, currentDate.getDayOfWeek(), localDate);
                    updatePaymentContribution(payment, currentMonthString);
                    ShareOuts shares = shareOutsRepository.save(shareOuts);
                    //add share outs to share  outs payment
                    addShareOutsPayment(shares, currentMonthString);
                } else {
                    log.info("SHARE OUT FOUND ==== OLD AMOUNT {}, NEW AMOUNT {} FOR {} IN CASE OF CORE ACCOUNT {}", checkCurrentShareOut.getAmount(), payment.getAmount(), payment.getPhoneNumber(), payment.getCoreAccount());
                    checkCurrentShareOut.setPaymentDate(localDate);
                    checkCurrentShareOut.setCoreAccount(checkCurrentShareOut.getCoreAccount());
                    checkCurrentShareOut.setPaymentStatus(checkCurrentShareOut.getPaymentStatus());
                    checkCurrentShareOut.setAmount(checkCurrentShareOut.getAmount() + payment.getAmount());
                    checkCurrentShareOut.setNewBalance(checkCurrentShareOut.getAmount());
                    updatePaymentContribution(payment, currentMonthString);
                    ShareOuts shares = shareOutsRepository.save(checkCurrentShareOut);
                    updateShareOutsPayment(shares, currentMonthString);
                }

            }

        }
    }

    private void updateShareOutsPayment(ShareOuts shares, String currentMonthString) {
        log.info("trying to update share out payment ------- ");
        ShareOutsPayment shareOutsPayment = shareOutsPaymentRepository.findFirstByGroupIdAndPhoneNumberAndPaymentStatusAndSoftDeleteFalseOrderByIdAsc(shares.getGroupId(), shares.getPhoneNumber(), PaymentEnum.PAYMENT_SUCCESS.name());
        if (!(shareOutsPayment == null)) {
            log.info(" SHARE OUT PAYMENT FOUND ====  OLD CONTRIBUTION old {}, NEW CONTRIBUTION {}", shareOutsPayment.getTotalContribution(), shares.getAmount());
            shareOutsPayment.setCurrentMonth(currentMonthString);
            shareOutsPayment.setTotalContribution(shares.getAmount());
            shareOutsPaymentRepository.save(shareOutsPayment);
            updateShareOutTable(shares);
        }
    }

    private void updatePaymentContribution(ContributionPayment contributionPayment, String currentMonthString) {
        contributionPayment.setLastModifiedDate(new Date());
        contributionPayment.setTransactionDate(new Date());
        contributionPayment.setContribution('Y');
        contributionPayment.setShareOut('Y');
        contributionPayment.setSharesCompleted('Y');
        contributionPayment.setMonth(currentMonthString.toLowerCase());
        paymentRepository.save(contributionPayment);
    }

    //    @Scheduled(fixedDelay = 1000)
//    @Scheduled(fixedDelay = 1000)
    public void monthlyShareOutPayment() {
        LocalDate localDate = LocalDate.now();
        // Get the current month
        Month currentMonth = localDate.getMonth();
        // Get the previous month
        LocalDate previousMonthDate = localDate.minusMonths(1);
        Month previousMonth = previousMonthDate.getMonth();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM");
        String currentMonthString = localDate.format(formatter);
        String previousMonthString = previousMonthDate.format(formatter);
        List<ShareOuts> shareOutsList = shareOutsRepository.unExecutedSharesOut(currentMonthString.toLowerCase());
        if (!shareOutsList.isEmpty()) {
            for (ShareOuts share :
                    shareOutsList) {
                Optional<ShareOuts> optionalShareOuts = shareOutsRepository.getUnExecutedSharesPerMonthForMember(share.getGroupId(), share.getPhoneNumber(), currentMonthString.toLowerCase());
                log.info("contribution Share PHONE {}, ==== AMOUNT {}, GROUP {}, payment ID {}", share.getPhoneNumber(), share.getAmount(), share.getGroupId(), share.getPaymentId());

                if (optionalShareOuts.isPresent()) {

                    ShareOuts shareOuts = optionalShareOuts.get();
                    log.info("member share out present {}, -------- amount {} ", shareOuts.getPhoneNumber(), shareOuts.getAmount());
//                    ShareOutsPayment shareOutsPayment = shareOutsPaymentRepository.findFirstByGroupIdAndPhoneNumberAndPaymentStatusAndSoftDeleteFalseOrderByIdDesc(share.getGroupId(), share.getPhoneNumber(), PaymentEnum.PAYMENT_SUCCESS.name());
//                    if (!(shareOutsPayment == null)) {
//                        //update share out for payment
//                        shareOutsPayment.setMonthSatisfied('Y');
//                        shareOutsPayment.setCurrentMonth(share.getMonth());
//                        shareOutsPayment.setLastModifiedDate(new Date());
//                        shareOutsPayment.setLastModifiedBy("SYSTEM");
//                        shareOutsPayment.setTotalContribution(shareOuts.getNewBalance());
//                        shareOutsPayment.setPhoneNumber(shareOuts.getPhoneNumber());
//                        shareOutsPayment.setCoreAccount(shareOuts.getCoreAccount());
//                        shareOutsPayment.setPaymentStatus(PaymentEnum.PAYMENT_SUCCESS.name());
//                        updateShareOutTable(shareOuts);
//                        shareOutsPaymentRepository.save(shareOutsPayment);
//                    } else {
//                        //new share out for payment
//                        updateShareOutTable(shareOuts);
//                        addNewShareOutsPayment(shareOuts, currentMonthString);
//                    }
                } else {
                    return;
                }

            }

        }
    }

    private void updateShareOutTable(ShareOuts shareOuts) {
        shareOuts.setLastModifiedBy("System");
        shareOuts.setLastModifiedDate(new Date());
        shareOuts.setExecuted('Y');
        shareOuts.setSharOutStatus("INACTIVE");
        shareOutsRepository.save(shareOuts);
    }

    private void addShareOutsPayment(ShareOuts shareOuts, String currentMonthString) {
        log.info("INITIAL SHARE OUT PAYMENT FOR {} IN CASE OF CORE ACCOUNT {} === ", shareOuts.getPhoneNumber(), shareOuts.getCoreAccount());
        ShareOutsPayment shareOutsPayment = shareOutsPaymentRepository.findFirstByGroupIdAndPhoneNumberAndPaymentStatusAndSoftDeleteFalseOrderByIdAsc(shareOuts.getGroupId(), shareOuts.getPhoneNumber(), PaymentEnum.PAYMENT_SUCCESS.name());
        if (shareOutsPayment == null) {
            log.info("SHARE OUT PAYMENT NOT FOUND!!! ADDING NEW AMOUNT TO PAYMENT {}", shareOuts.getAmount());
            ShareOutsPayment outsPayment = new ShareOutsPayment(shareOuts.getGroupId(), shareOuts.getPhoneNumber(), shareOuts.getCoreAccount(), currentMonthString, shareOuts.getAmount(), shareOuts);
            shareOutsPaymentRepository.save(outsPayment);
            updateShareOutTable(shareOuts);
        } else {
            log.info("share out payment found ***** should update from other script ------- ");
            return;
//            shareOutsPayment.setTotalContribution(shareOutsPayment.getTotalContribution() + shareOuts.getAmount());
//            shareOutsPaymentRepository.save(shareOutsPayment);
//            updateShareOutTable(shareOuts);
        }
    }
}
