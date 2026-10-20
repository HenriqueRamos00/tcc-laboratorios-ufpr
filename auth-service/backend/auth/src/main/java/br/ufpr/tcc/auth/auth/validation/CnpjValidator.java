package br.ufpr.tcc.auth.auth.validation;

public final class CnpjValidator {
    private static final int[] FIRST_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private CnpjValidator() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public static boolean isValid(String value) {
        String digits = normalize(value);
        if (digits.length() != 14) return false;
        if (digits.chars().distinct().count() == 1) return false;

        String base = digits.substring(0, 12);
        int first = calculate(base, FIRST_WEIGHTS);
        int second = calculate(base + first, SECOND_WEIGHTS);
        return digits.endsWith("" + first + second);
    }

    private static int calculate(String base, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += Character.digit(base.charAt(i), 10) * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
