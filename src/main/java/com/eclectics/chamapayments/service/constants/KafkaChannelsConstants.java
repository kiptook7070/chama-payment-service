package com.eclectics.chamapayments.service.constants;

/**
 * Define kafka topics to be used by the Stream Bridge for
 * publishing.
 * The names conform to the Consumers in the respective services.
 *
 * @author wnganga
 * @created 15/04/2022
 */
public class KafkaChannelsConstants {

    public static final String SEND_POST_BANK_TEXT_SMS_TOPIC = "sendPostBankText-in-0";
    public static final String SEND_EMAIL_STATEMENT_TOPIC = "sendPostBankEmail-in-0";
    public static final String CALLBACK_TOPIC = "callback-topic";
    public static final String WRITE_OFF_LOANS_AND_PENALTIES_TOPIC = "writeOffLoansAndPenalties-in-0";

}
