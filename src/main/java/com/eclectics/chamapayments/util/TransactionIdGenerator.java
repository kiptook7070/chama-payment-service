package com.eclectics.chamapayments.util;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;

@UtilityClass
public class TransactionIdGenerator {

    public static String generateTransactionId(String prefix) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        sb.append(saltRandom(generateNineDigit()));
        return sb.toString();
    }

    private static String saltRandom(String randomNumber) {
        char[] chars = new char[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < randomNumber.length(); i++) {
            if (i % 2 == 0) {
                sb.append(randomNumber.charAt(i));
                continue;
            }

            sb.append(chars[generateOneDigit()]);
        }

        return sb.toString();
    }

    private static int generateOneDigit() {
        SecureRandom r = new SecureRandom();
        int low = 0;
        int high = 25;
        return r.nextInt(high - low) + low;
    }

    private static String generateNineDigit() {
        SecureRandom r = new SecureRandom();
        int low = 100000000;
        int high = 999999999;
        int result = r.nextInt(high - low) + low;
        return String.valueOf(result);
    }


}
