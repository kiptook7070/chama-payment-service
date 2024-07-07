package com.eclectics.chamapayments.service.constants;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StringConstantsUtil {
    public static final String UPPER_AND_LOWER_CASE_MATCH = "^[[a-zA-z][\\s+]]*$";
    public static final String LOWER_CASE_MATCH = "^[[a-z][\\s+]]*$";

    public static final String PHONE_NUMBER_MATCH = "^(25[54])([71])\\d{8}$";
}
