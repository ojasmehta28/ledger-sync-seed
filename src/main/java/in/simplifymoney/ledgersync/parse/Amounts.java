package in.simplifymoney.ledgersync.parse;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Amounts {

    private Amounts() {}

    private static final Pattern AMOUNT =
            Pattern.compile("(?:Rs\\.?|INR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern BALANCE = Pattern.compile(
            "(?:Avl\\s*Bal|Available\\s*Balance|BalAvl|Avl\\s*Limit)\\s*:?\\s*"
                    + "(?:Rs\\.?|INR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE);

    public static BigDecimal first(String body) {
        Matcher m = AMOUNT.matcher(body);

        while (m.find()) {
            String matched = m.group(0);

            // Never use a balance figure as the transaction amount.
            if (isBalanceContext(body, m.start())) {
                continue;
            }

            return toDecimal(m.group(1));
        }

        return null;
    }

    public static BigDecimal statedBalance(String body) {
        Matcher m = BALANCE.matcher(body);
        if (!m.find()) return null;
        return toDecimal(m.group(1));
    }

    private static boolean isBalanceContext(String body, int amountStart) {
        int start = Math.max(0, amountStart - 30);
        String prefix = body.substring(start, amountStart).toLowerCase();

        return prefix.contains("avl bal")
                || prefix.contains("available balance")
                || prefix.contains("balavl")
                || prefix.contains("avl limit");
    }

    private static BigDecimal toDecimal(String raw) {
        return new BigDecimal(raw.replace(",", "")).setScale(2);
    }
}