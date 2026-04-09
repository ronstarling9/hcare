package com.hcare.integration.billing;

/**
 * Static utility for validating 10-digit NPIs per CMS specification.
 *
 * <p>Validation uses the standard Luhn algorithm with the "80840" prefix prepended as required by
 * the CMS NPI specification.
 */
public final class NpiValidator {

    private NpiValidator() {}

    /**
     * Returns {@code true} if the given NPI is a valid 10-digit NPI that passes the Luhn check
     * with the "80840" prefix prepended.
     *
     * @param npi the NPI string to validate
     * @return {@code true} if valid; {@code false} otherwise
     */
    public static boolean isValid(String npi) {
        if (npi == null || !npi.matches("\\d{10}")) {
            return false;
        }
        String withPrefix = "80840" + npi;
        return luhnCheck(withPrefix);
    }

    private static boolean luhnCheck(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = number.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}
