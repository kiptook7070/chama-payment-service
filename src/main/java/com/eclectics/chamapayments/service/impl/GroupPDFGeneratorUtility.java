package com.eclectics.chamapayments.service.impl;

import com.eclectics.chamapayments.model.ContributionPayment;
import com.eclectics.chamapayments.model.OtherChannelsBalances;
import com.eclectics.chamapayments.wrappers.response.MemberLoanDisbursedList;
import com.google.gson.JsonArray;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

@Service
public class GroupPDFGeneratorUtility {
    private static Logger logger = LoggerFactory.
            getLogger(GroupPDFGeneratorUtility.class);

    public static ByteArrayInputStream cbsGroupPDFReport(String acctName, String regNumber, String ledgerBalance, String actualBalance, String address, String groupAccount, JsonArray tranData, double savingBalance, double welfareBalance, double fineBalance, double projectBalance) {
//        ClassPathResource resource = new ClassPathResource("images/logo.jpg");
        Document document = new Document(PageSize.A2, 0, 0, 0, 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {

            PdfWriter.getInstance(document, out);
            document.open();
            Font font = FontFactory
                    .getFont(FontFactory.COURIER, 12, BaseColor.BLACK);

            groupParagraphs(acctName, groupAccount, ledgerBalance, font, document, fineBalance, savingBalance, welfareBalance, projectBalance);

            PdfPTable table = new PdfPTable(8);
            //todo:: Add PDF Table Header
            cbsGroupHeaders(table);
            cbsPdfReport(tranData, document, table);
        } catch (DocumentException e) {
            logger.error(e.toString());
        }

        return new ByteArrayInputStream(out.toByteArray());
    }


    private static void cbsGroupHeaders(PdfPTable table) {
        Stream.of("DATE", "TRXN REF", "BRANCH", "MONEY IN", "MONEY OUT", "AMOUNT", "BALANCE", "PARTICULARS")
                .forEach(cbsGroupHeaderTiltle ->
                {
                    PdfPCell header = new PdfPCell();
                    Font headFont = FontFactory.
                            getFont(FontFactory.HELVETICA_BOLD);
                    header.setBackgroundColor(BaseColor.LIGHT_GRAY);
                    header.setHorizontalAlignment(Element.ALIGN_JUSTIFIED);
                    header.setBorderWidth(0);
                    header.setPhrase(new Phrase(cbsGroupHeaderTiltle, headFont));
                    table.addCell(header);
                });
    }

    private static void cbsPdfReport(JsonArray tranData, Document document, PdfPTable table) throws DocumentException {

        tranData.forEach(data -> {
            String refNo = data.getAsJsonObject().get("refNo").getAsString();
            String branch = data.getAsJsonObject().get("branch").getAsString();
            String narration = data.getAsJsonObject().get("tran_DESC").getAsString().toLowerCase();
            double amountPaid = 0.0;
            double creditAmount = data.getAsJsonObject().get("creditAmount").getAsDouble();
            double debitAmount = data.getAsJsonObject().get("debitAmount").getAsDouble();

            if (creditAmount < 0) {
                creditAmount = 0.0;
            }
            if (debitAmount < 0) {
                debitAmount = 0.0;
            }
            if (creditAmount > 0) {
                amountPaid = creditAmount;
            } else if (debitAmount > 0) {
                amountPaid = debitAmount;
            }
            String formatAmountPaid = decimalFormatValue(amountPaid);

            double runningBalance = data.getAsJsonObject().get("runningBalance").getAsDouble();
            if (runningBalance < 0) {
                runningBalance = 0.0;
            }

            creditAmount = formatAmount(creditAmount);
            String formatCreditAmount = decimalFormatValue(creditAmount);

            debitAmount = formatAmount(debitAmount);
            String formatDebitAmount = decimalFormatValue(debitAmount);
            runningBalance = formatAmount(runningBalance);
            String formatRunningBalance = decimalFormatValue(runningBalance);

            String tranDate = data.getAsJsonObject().get("tranDate").getAsString();
//            "1.DATE", "2.TRXN REF", "3.BRANCH",  "4.MONEY IN", "5.MONEY OUT", "6.AMOUNT", "7.BALANCE", "8.PARTICULARS",

//           1. DATE
            PdfPCell transactionDateCell = new PdfPCell(new Phrase
                    (tranDate));
            transactionDateCell.setVerticalAlignment(Element.ALIGN_LEFT);
            transactionDateCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            transactionDateCell.setPaddingRight(1);
            table.addCell(transactionDateCell);

//           2. TRXN
            PdfPCell referenceCell = new PdfPCell(new Phrase(refNo));
            referenceCell.setPaddingLeft(1);
            referenceCell.setVerticalAlignment(Element.ALIGN_LEFT);
            referenceCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(referenceCell);

//           3. BRANCH
            PdfPCell branchCell = new PdfPCell(new Phrase
                    (branch));
            branchCell.setPaddingLeft(1);
            branchCell.setVerticalAlignment(Element.ALIGN_LEFT);
            branchCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(branchCell);


//           4. MONEY IN
            PdfPCell moneyInCell = new PdfPCell(new Phrase
                    (String.valueOf(formatCreditAmount)));
            moneyInCell.setPaddingLeft(1);
            moneyInCell.setVerticalAlignment(Element.ALIGN_LEFT);
            moneyInCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(moneyInCell);

//          5.  MONEY OUT
            PdfPCell moneyOutCell = new PdfPCell(new Phrase
                    (String.valueOf(formatDebitAmount)));
            moneyOutCell.setPaddingLeft(1);
            moneyOutCell.setVerticalAlignment(Element.ALIGN_LEFT);
            moneyOutCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(moneyOutCell);

//          6.  AMOUNT PAID
            PdfPCell amountPaidCell = new PdfPCell(new Phrase
                    (String.valueOf(formatAmountPaid)));
            amountPaidCell.setPaddingLeft(1);
            amountPaidCell.setVerticalAlignment(Element.ALIGN_LEFT);
            amountPaidCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(amountPaidCell);

//           7. RUNNING BALANCE
            PdfPCell balanceCell = new PdfPCell(new Phrase
                    (String.valueOf(formatRunningBalance)));
            balanceCell.setPaddingLeft(1);
            balanceCell.setVerticalAlignment(Element.ALIGN_LEFT);
            balanceCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(balanceCell);

            //   8. PARTICULARS
            PdfPCell narrationCell = new PdfPCell(new Phrase
                    (narration));
            narrationCell.setPaddingLeft(1);
            narrationCell.setVerticalAlignment(Element.ALIGN_LEFT);
            narrationCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(narrationCell);

        });

        document.add(table);

        document.close();
    }


    private static void groupParagraphs(String acctName, String groupAccount, String actualBalance, Font font, Document document, double fineBalance, double savingBalance, double welfareBalance, double projectBalance) throws DocumentException {
        String formatActualBalance = decimalFormatValue(Double.parseDouble(actualBalance));
        String formatFineBalance = decimalFormatValue(fineBalance);
        String formatProjectBalance = decimalFormatValue(projectBalance);
        String formatWelfareBalance = decimalFormatValue(welfareBalance);
        String formatSavingBalance = decimalFormatValue(savingBalance);
        Paragraph groupTitle = new Paragraph("Group Statement Report", font);
        Paragraph group = new Paragraph("Name : " + acctName, font);
        Paragraph account = new Paragraph("Group Account : " + groupAccount, font);
        Paragraph balance = new Paragraph("Group Balance : " + formatActualBalance, font);
        Paragraph contribution = new Paragraph("Group Contribution Summary", font);
        Paragraph totalFineBalance = new Paragraph("Fines : " + formatFineBalance, font);
        Paragraph totalProjectBalance = new Paragraph("Project : " + formatProjectBalance, font);
        Paragraph totalWelfareBalance = new Paragraph("Welfare : " + formatWelfareBalance, font);
        Paragraph totalSavingBalance = new Paragraph("Savings : " + formatSavingBalance, font);
        groupTitle.setAlignment(Element.ALIGN_CENTER);
        group.setAlignment(Element.ALIGN_CENTER);
        account.setAlignment(Element.ALIGN_CENTER);
        balance.setAlignment(Element.ALIGN_CENTER);
        contribution.setAlignment(Element.ALIGN_CENTER);
        totalFineBalance.setAlignment(Element.ALIGN_CENTER);
        totalProjectBalance.setAlignment(Element.ALIGN_CENTER);
        totalWelfareBalance.setAlignment(Element.ALIGN_CENTER);
        totalSavingBalance.setAlignment(Element.ALIGN_CENTER);
        document.add(groupTitle);
        document.add(group);
        document.add(account);
        document.add(balance);
        document.add(groupTitle);
        document.add(totalFineBalance);
        document.add(totalProjectBalance);
        document.add(totalWelfareBalance);
        document.add(totalSavingBalance);
        document.add(Chunk.NEWLINE);
    }


    private static void contributionSummary(double savingBalance, double welfareBalance, double fineBalance, double projectBalance, Font font, Document document) throws DocumentException {
        String formatFineBalance = decimalFormatValue(fineBalance);
        String formatProjectBalance = decimalFormatValue(projectBalance);
        String formatWelfareBalance = decimalFormatValue(welfareBalance);
        String formatSavingBalance = decimalFormatValue(savingBalance);

        Paragraph summary = new Paragraph("Contributions Summary ", font);
        Paragraph sBalance = new Paragraph("Savings : " + formatSavingBalance, font);
        Paragraph wBalance = new Paragraph("Welfare : " + formatWelfareBalance, font);
        Paragraph pBalance = new Paragraph("Project : " + formatProjectBalance, font);
        Paragraph fBalance = new Paragraph("Fine    : " + formatFineBalance, font);

        summary.setAlignment(Element.ALIGN_CENTER);
        sBalance.setAlignment(Element.ALIGN_CENTER);
        wBalance.setAlignment(Element.ALIGN_CENTER);
        pBalance.setAlignment(Element.ALIGN_CENTER);
        fBalance.setAlignment(Element.ALIGN_CENTER);

        document.add(summary);
        document.add(sBalance);
        document.add(wBalance);
        document.add(pBalance);
        document.add(fBalance);
        document.add(Chunk.NEWLINE);
    }

    private static void generateHeaders(PdfPTable table) {
        Stream.of("DATE", "TRXN REF", "CATEGORY", "NARRATION", "AMOUNT")
                .forEach(headerTitle ->
                {
                    PdfPCell header = new PdfPCell();
                    Font headFont = FontFactory.
                            getFont(FontFactory.HELVETICA_BOLD);
                    header.setBackgroundColor(BaseColor.LIGHT_GRAY);
                    header.setHorizontalAlignment(Element.ALIGN_JUSTIFIED);
                    header.setBorderWidth(0);
                    header.setPhrase(new Phrase(headerTitle, headFont));
                    table.addCell(header);
                });
    }


    private static void getMemberParagraph(String memberName, String groupName, String phoneNumber, Font font, Document document) throws DocumentException {
        Paragraph title = new Paragraph("Mchama User Statement Report", font);
        Paragraph group = new Paragraph("Group Name: " + groupName, font);
        Paragraph name = new Paragraph("Full Name: " + memberName, font);
        Paragraph mobile = new Paragraph("Mobile Number: " + phoneNumber, font);

        title.setAlignment(Element.ALIGN_CENTER);
        group.setAlignment(Element.ALIGN_CENTER);
        name.setAlignment(Element.ALIGN_CENTER);
        mobile.setAlignment(Element.ALIGN_CENTER);

        document.add(title);
        document.add(group);
        document.add(name);
        document.add(mobile);
        document.add(Chunk.NEWLINE);
    }


    public static ByteArrayInputStream mchamaMemberPDFReport(List<ContributionPayment> contributionPayments, String memberName, String groupName, String phoneNumber, double savingBalance, double welfareBalance, double projectBalance, double loanBalance, double fineBalance) {

        Document document = new Document(PageSize.A3, 0, 0, 0, 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();
            Font font = FontFactory
                    .getFont(FontFactory.COURIER, 12, BaseColor.BLACK);

            getMemberParagraph(memberName, groupName, phoneNumber, font, document);

            contributionSummary(savingBalance, welfareBalance, fineBalance, projectBalance, font, document);

            PdfPTable table = new PdfPTable(5);
            // Add PDF Table Header ->
            generateHeaders(table);

            memberReport(contributionPayments, document, table);
        } catch (DocumentException e) {
            logger.error(e.toString());
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    private static void memberReport(List<ContributionPayment> contributionPaymentList, Document document, PdfPTable table) throws DocumentException {
        for (ContributionPayment payment : contributionPaymentList) {
            String pattern = "yyyy-MM-dd";
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
            if (payment.getCreatedOn() == null) {
                payment.setCreatedOn(new Date());
            }
            String paymentDate = simpleDateFormat.format(payment.getCreatedOn());
            double amount = formatAmount(Double.valueOf(payment.getAmount()));

            String formatAmount = decimalFormatValue(amount);

//            "1.DATE", "2.TRXN REF", "3.CATEGORY", "4.NARRATION", "5.AMOUNT"
            //1. DATE
            PdfPCell transactionDateCell = new PdfPCell(new Phrase
                    (paymentDate));
            transactionDateCell.setVerticalAlignment(Element.ALIGN_LEFT);
            transactionDateCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            transactionDateCell.setPaddingRight(1);
            table.addCell(transactionDateCell);

            //2. TRXN
            PdfPCell referenceCell = new PdfPCell(new Phrase(payment.getTransactionId()));
            referenceCell.setPaddingLeft(1);
            referenceCell.setVerticalAlignment(Element.ALIGN_LEFT);
            referenceCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(referenceCell);

            //3. CATEGORY
            PdfPCell categoryCell = new PdfPCell(new Phrase
                    (payment.getPaymentType()));
            categoryCell.setPaddingLeft(1);
            categoryCell.setVerticalAlignment(Element.ALIGN_LEFT);
            categoryCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(categoryCell);

            //4. NARRATION
            PdfPCell narrationCell = new PdfPCell(new Phrase
                    (payment.getNarration()));
            narrationCell.setPaddingLeft(1);
            narrationCell.setVerticalAlignment(Element.ALIGN_LEFT);
            narrationCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(narrationCell);

            //5.  AMOUNT PAID
            PdfPCell amountPaidCell = new PdfPCell(new Phrase
                    (String.valueOf(formatAmount)));
            amountPaidCell.setPaddingLeft(1);
            amountPaidCell.setVerticalAlignment(Element.ALIGN_LEFT);
            amountPaidCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(amountPaidCell);

        }
        document.add(table);

        document.close();
    }

    public static ByteArrayInputStream channelTransactionsReport(String groupName, String groupAccount, List<OtherChannelsBalances> channelsBalancesList, double groupBalances) {
        // ClassPathResource resource = new ClassPathResource("images/logo.jpg");
        Document channelDocument = new Document(PageSize.A3, 0, 0, 0, 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {

            PdfWriter.getInstance(channelDocument, out);
            channelDocument.open();
            Font font = FontFactory
                    .getFont(FontFactory.COURIER, 12, BaseColor.BLACK);

            channelDocumentParagraphs(groupName, groupAccount, font, channelDocument, groupBalances);

            PdfPTable channelTable = new PdfPTable(7);
            //todo:: Add PDF Table Header ->
            channelDocumentHeaders(channelTable);
            channelDocumentReport(channelsBalancesList, channelDocument, channelTable);
        } catch (DocumentException e) {
            logger.error(e.toString());
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    private static void channelDocumentReport(List<OtherChannelsBalances> channelsBalancesList, Document channelDocument, PdfPTable channelTable) throws DocumentException {

        channelsBalancesList.forEach(transaction -> {
            String formatCreditBalance = decimalFormatValue(transaction.getCreditAmount());
            PdfPCell transactionDateCell = new PdfPCell(new Phrase
                    (transaction.getTranDate()));
            transactionDateCell.setVerticalAlignment(Element.ALIGN_LEFT);
            transactionDateCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            transactionDateCell.setPaddingRight(1);
            channelTable.addCell(transactionDateCell);

            PdfPCell transactionIdCell = new PdfPCell(new Phrase
                    (transaction.getTransactionId()));
            transactionIdCell.setVerticalAlignment(Element.ALIGN_LEFT);
            transactionIdCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            transactionIdCell.setPaddingRight(1);
            channelTable.addCell(transactionIdCell);

            PdfPCell branchCell = new PdfPCell(new Phrase
                    (transaction.getBranch()));
            branchCell.setVerticalAlignment(Element.ALIGN_LEFT);
            branchCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            branchCell.setPaddingRight(1);
            channelTable.addCell(branchCell);

            PdfPCell channelCell = new PdfPCell(new Phrase
                    (transaction.getChannel()));
            channelCell.setVerticalAlignment(Element.ALIGN_LEFT);
            channelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            channelCell.setPaddingRight(1);
            channelTable.addCell(channelCell);

            PdfPCell creditAmountCell = new PdfPCell(new Phrase
                    (String.valueOf(formatCreditBalance)));
            creditAmountCell.setVerticalAlignment(Element.ALIGN_LEFT);
            creditAmountCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            creditAmountCell.setPaddingRight(1);
            channelTable.addCell(creditAmountCell);

            final PdfPCell transactionActedOnCell = getPdfPCell(transaction);
            channelTable.addCell(transactionActedOnCell);

            PdfPCell transactionDescriptionCell = new PdfPCell(new Phrase
                    (transaction.getTransactionDescription().toLowerCase()));
            transactionDescriptionCell.setVerticalAlignment(Element.ALIGN_LEFT);
            transactionDescriptionCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            transactionDescriptionCell.setPaddingRight(1);
            channelTable.addCell(transactionDescriptionCell);

        });

        channelDocument.add(channelTable);

        channelDocument.close();
    }

    private static PdfPCell getPdfPCell(OtherChannelsBalances transaction) {
        String transactionActedOn;
        if (transaction.getTransactionActedOn().equals(true)) {
            transactionActedOn = "COMPLETED";
        } else {
            transactionActedOn = "IN PROGRESS";
        }

        PdfPCell transactionActedOnCell = new PdfPCell(new Phrase
                (transactionActedOn));
        transactionActedOnCell.setVerticalAlignment(Element.ALIGN_LEFT);
        transactionActedOnCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        transactionActedOnCell.setPaddingRight(1);
        return transactionActedOnCell;
    }

    private static void channelDocumentHeaders(PdfPTable table) {

        Stream.of("DATE", "TRXN REF", "BRANCH", "CHANNEL", "AMOUNT", "ASSIGNING", "DESCRIPTION")
                .forEach(channelHeaderTitle ->
                {
                    PdfPCell header = new PdfPCell();
                    Font headFont = FontFactory.
                            getFont(FontFactory.HELVETICA_BOLD);
                    header.setBackgroundColor(BaseColor.LIGHT_GRAY);
                    header.setHorizontalAlignment(Element.ALIGN_JUSTIFIED);
                    header.setBorderWidth(0);
                    header.setPhrase(new Phrase(channelHeaderTitle, headFont));
                    table.addCell(header);
                });
    }

    private static void channelDocumentParagraphs(String groupName, String groupAccount, Font font, Document channelDocument, double groupBalances) throws DocumentException {
        String formatBalance = decimalFormatValue(groupBalances);
        Paragraph channelTitle = new Paragraph("Channel Transaction Report", font);
        Paragraph group = new Paragraph("Name : " + groupName, font);
        Paragraph account = new Paragraph("Group Account : " + groupAccount, font);
        Paragraph channelBalance = new Paragraph("Channel Balance : " + formatBalance, font);
        channelTitle.setAlignment(Element.ALIGN_CENTER);
        group.setAlignment(Element.ALIGN_CENTER);
        account.setAlignment(Element.ALIGN_CENTER);
        channelBalance.setAlignment(Element.ALIGN_CENTER);
        channelDocument.add(channelTitle);
        channelDocument.add(group);
        channelDocument.add(account);
        channelDocument.add(channelBalance);
        channelDocument.add(Chunk.NEWLINE);
    }


    public static double formatAmount(Double amount) {
        DecimalFormat decimalFormat = new DecimalFormat("#.####");
        // Formatting the number to two decimal places
        double formattedAmount = Double.parseDouble(decimalFormat.format(amount));
        return formattedAmount;
    }

    public static ByteArrayInputStream loanDisbursedReport(String groupName, double borrowed, double repaymentAmount, double interest, List<MemberLoanDisbursedList> memberLoanDisbursedListList) {
        // ClassPathResource resource = new ClassPathResource("images/logo.jpg");
        Document loanDocument = new Document(PageSize.A3, 0, 0, 0, 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {

            PdfWriter.getInstance(loanDocument, out);
            loanDocument.open();
            Font font = FontFactory
                    .getFont(FontFactory.COURIER, 12, BaseColor.BLACK);

            loansDisbursedParagraphs(groupName, borrowed, repaymentAmount, interest, font, loanDocument);

            PdfPTable loanDisbursedTable = new PdfPTable(7);
            //todo:: Add PDF Table Header ->
            loanDisbursedDocumentHeaders(loanDisbursedTable);
            loanDisbursedReport(memberLoanDisbursedListList, loanDocument, loanDisbursedTable);
        } catch (DocumentException e) {
            logger.error(e.toString());
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    private static void loanDisbursedReport(List<MemberLoanDisbursedList> loansDisbursedList, Document loanDocument, PdfPTable loanDisbursedTable) throws DocumentException {
        loansDisbursedList.parallelStream().forEach(loansDisbursed -> {
            //TODO:: 1. DATE
            PdfPCell transactionDateCell = new PdfPCell(new Phrase
                    (loansDisbursed.getCreatedOn()));
            transactionDateCell.setVerticalAlignment(Element.ALIGN_LEFT);
            transactionDateCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            transactionDateCell.setPaddingRight(1);
            loanDisbursedTable.addCell(transactionDateCell);

            //TODO:: 2. MEMBER
            PdfPCell memberNameCell = new PdfPCell(new Phrase
                    (loansDisbursed.getMemberName()));
            memberNameCell.setVerticalAlignment(Element.ALIGN_LEFT);
            memberNameCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            memberNameCell.setPaddingRight(1);
            loanDisbursedTable.addCell(memberNameCell);

            //TODO:: 3. PHONE
            PdfPCell memberPhoneCell = new PdfPCell(new Phrase
                    (loansDisbursed.getMemberPhone()));
            memberPhoneCell.setVerticalAlignment(Element.ALIGN_LEFT);
            memberPhoneCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            memberPhoneCell.setPaddingRight(1);
            loanDisbursedTable.addCell(memberPhoneCell);

            //TODO:: 4. PRINCIPAL
            PdfPCell principalAmountCell = new PdfPCell(new Phrase
                    (loansDisbursed.getPrincipal()));
            principalAmountCell.setVerticalAlignment(Element.ALIGN_LEFT);
            principalAmountCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            principalAmountCell.setPaddingRight(1);
            loanDisbursedTable.addCell(principalAmountCell);

            //TODO:: 5. DUE AMOUNT
            PdfPCell dueAmountCell = new PdfPCell(new Phrase
                    (loansDisbursed.getDueamount()));
            dueAmountCell.setVerticalAlignment(Element.ALIGN_LEFT);
            dueAmountCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            dueAmountCell.setPaddingRight(1);
            loanDisbursedTable.addCell(dueAmountCell);

            //TODO:: 6. INTEREST
            PdfPCell interestAmountCell = new PdfPCell(new Phrase
                    (loansDisbursed.getInterest()));
            interestAmountCell.setVerticalAlignment(Element.ALIGN_LEFT);
            interestAmountCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            interestAmountCell.setPaddingRight(1);
            loanDisbursedTable.addCell(interestAmountCell);

            //TODO:: STATUS
            PdfPCell statusCell = new PdfPCell(new Phrase
                    (loansDisbursed.getLoanStatus()));
            statusCell.setVerticalAlignment(Element.ALIGN_LEFT);
            statusCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            statusCell.setPaddingRight(1);
            loanDisbursedTable.addCell(statusCell);

        });

        loanDocument.add(loanDisbursedTable);

        loanDocument.close();
    }

    private static void loanDisbursedDocumentHeaders(PdfPTable loanDisbursedTable) {

        Stream.of("DATE", "MEMBER", "PHONE", "PRINCIPAL", "DUE AMOUNT", "INTEREST", "STATUS")
                .forEach(loanDisbursedHeaderTitle ->
                {
                    PdfPCell header = new PdfPCell();
                    Font headFont = FontFactory.
                            getFont(FontFactory.HELVETICA_BOLD);
                    header.setBackgroundColor(BaseColor.LIGHT_GRAY);
                    header.setHorizontalAlignment(Element.ALIGN_JUSTIFIED);
                    header.setBorderWidth(0);
                    header.setPhrase(new Phrase(loanDisbursedHeaderTitle, headFont));
                    loanDisbursedTable.addCell(header);
                });
    }


    private static void loansDisbursedParagraphs(String groupName, double borrowed, double repaymentAmount, double interest, Font font, Document loanDocument) throws DocumentException {
        Paragraph loanTitle = new Paragraph("Group Loan Disbursed Report", font);
        Paragraph group = new Paragraph("Group Name : " + groupName, font);
        Paragraph totalBorrowed = new Paragraph("Loan Borrowed : " + borrowed, font);
        Paragraph repayment = new Paragraph("Due Amount: " + repaymentAmount, font);
        Paragraph totalInterest = new Paragraph("Interest : " + interest, font);

        loanTitle.setAlignment(Element.ALIGN_CENTER);
        group.setAlignment(Element.ALIGN_CENTER);
        totalBorrowed.setAlignment(Element.ALIGN_CENTER);
        totalInterest.setAlignment(Element.ALIGN_CENTER);
        repayment.setAlignment(Element.ALIGN_CENTER);
        loanTitle.add(loanTitle);
        loanDocument.add(group);
        loanDocument.add(totalBorrowed);
        loanDocument.add(repayment);
        loanDocument.add(totalInterest);
        loanDocument.add(Chunk.NEWLINE);
    }

    public static String decimalFormatValue(double amount) {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        return decimalFormat.format(amount);
    }
}
