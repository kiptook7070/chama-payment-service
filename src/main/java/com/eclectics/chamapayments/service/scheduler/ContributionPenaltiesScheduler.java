package com.eclectics.chamapayments.service.scheduler;


import com.eclectics.chamapayments.model.*;
import com.eclectics.chamapayments.repository.*;
import com.eclectics.chamapayments.service.ChamaKycService;
import com.eclectics.chamapayments.service.NotificationService;
import com.eclectics.chamapayments.wrappers.response.GroupMemberWrapper;
import com.eclectics.chamapayments.wrappers.response.GroupWrapper;
import com.eclectics.chamapayments.wrappers.response.MemberWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionPenaltiesScheduler {

    private final ChamaKycService chamaKycService;
    private final PenaltyRepository penaltyRepository;
    private final NotificationService notificationService;
    private final ContributionRepository contributionRepository;
    private final ContributionsPaymentRepository contributionsPaymentRepository;
    private final ContributionSchedulePaymentRepository contributionSchedulePaymentRepository;
    private final OutstandingContributionPaymentRepository outstandingContributionPaymentRepository;

    /**
     * Scheduler for generating penalties for members in a group.
     * This is for weekly penalties.
     */

    /**
     * Scheduler for generating penalties for members in a group.
     * This is for monthly penalties.
     */

    /**
     * Helper method
     *
     * @param contributionSchedulePayment the scheduled contribution payment

    /**
     * Checks if there is an existing penalty for the contribution scheduled payment.
     *
     * @param groupMembers                the members in a group
     * @param contributionSchedulePayment the contribution scheduled payment
     * @param amount                      the amount to be paid as penalty
     * @param contributions               the contribution object
     */
    private void createPenalty(List<GroupMemberWrapper> groupMembers, ContributionSchedulePayment contributionSchedulePayment, double amount, Contributions contributions) {
        groupMembers.forEach(groupMemberWrapper -> {
            String scheduledId = contributionSchedulePayment.getContributionScheduledId();

            Penalty penalty = penaltyRepository.findByTransactionId(scheduledId.concat(groupMemberWrapper.getPhoneNumber()));
            if (penalty != null) return;

            String expectedDate = contributionSchedulePayment.getExpectedContributionDate();
            DateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
            try {
                Date contributionDate = formatter.parse(expectedDate);

                if (groupMemberWrapper.getCreatedOn().getTime() > contributionDate.getTime()) return;

                List<ContributionPayment> contributionPayments = contributionsPaymentRepository.findPaidScheduledContributions(groupMemberWrapper.getPhoneNumber(), scheduledId);

                int totalContributed =
                        contributionPayments.parallelStream().mapToInt(ContributionPayment::getAmount).sum();

                Long difference = (long) (contributions.getContributionAmount() - totalContributed);
                if (difference == 0) return;

                penalty = Penalty.builder()
                        .userId(groupMemberWrapper.getMemberId())
                        .amount(amount)
                        .isPaid(false)
                        .contributionId(contributions.getId())
                        .paymentPhoneNumber(groupMemberWrapper.getPhoneNumber())
                        .contributionName(contributions.getName())
                        .expectedPaymentDate(contributionSchedulePayment.getExpectedContributionDate())
                        .transactionId(scheduledId.concat(groupMemberWrapper.getPhoneNumber()))
                        .schedulePaymentId(scheduledId)
                        .build();

                // check if there is an outstanding contribution payment
                Optional<OutstandingContributionPayment> outstandingContributionPayment =
                        outstandingContributionPaymentRepository.findByContributionIdAndMemberId(contributions.getId(), groupMemberWrapper.getMemberId());

                OutstandingContributionPayment ocp;
                if (outstandingContributionPayment.isEmpty()) {
                    ocp = OutstandingContributionPayment.builder()
                            .contributionId(contributions.getId())
                            .dueAmount(difference.intValue())
                            .paidAmount(totalContributed)
                            .memberId(groupMemberWrapper.getMemberId())
                            .build();
                } else {
                    ocp = outstandingContributionPayment.get();
                    Integer currentAmount = ocp.getDueAmount();
                    ocp.setDueAmount(currentAmount + difference.intValue());
                    ocp.setLastModifiedDate(new Date());
                }

                penaltyRepository.save(penalty);
                log.info("Saved penalty...");
                outstandingContributionPaymentRepository.save(ocp);
                log.info("Saved outstanding OCP...");
                MemberWrapper member = chamaKycService.searchMonoMemberByPhoneNumber(groupMemberWrapper.getPhoneNumber());
                if (member == null) {
                    log.info("Group not found... on creation of a penalty");
                    return;
                }

                GroupWrapper group = chamaKycService.getMonoGroupById(groupMemberWrapper.getGroupId());

                if (group == null) return;

                String memberName = String.format("%s %s", member.getFirstname(), member.getLastname());
                try {
                    notificationService.sendPenaltyCreatedMessage(member.getPhonenumber(), memberName, scheduledId, group.getName(), amount, member.getLanguage());
                } catch (Exception e){
                    log.error("penalty created message failing..........{}",e.getMessage());
                }


                sendPenaltyCreatedToGroup(member.getPhonenumber(), memberName, groupMemberWrapper.getGroupId(), group.getName(), scheduledId, amount);
            } catch (ParseException e) {
                log.info("Date format not accepted... on creation of a penalty");
            }
        });
    }

    @Async
    public void sendPenaltyCreatedToGroup(String phoneNumber, String memberName, long groupId, String scheduledId, String groupName, double amount) {
        chamaKycService.getFluxMembersLanguageAndPhonesInGroup(groupId).toStream()
                .filter(pair -> !Objects.equals(pair.getFirst(), phoneNumber))
                .forEach(pair -> notificationService.sendPenaltyCreatedMessageToGroup(pair.getFirst(), memberName, scheduledId, amount, pair.getSecond(), groupName));
    }

}
