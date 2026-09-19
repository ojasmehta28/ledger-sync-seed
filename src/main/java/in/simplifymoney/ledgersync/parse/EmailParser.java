package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmailParser implements MessageParser {

    private static final Pattern TRANSACTION = Pattern.compile(
            "account ending\\s+(\\d{4})\\s+has been\\s+"
                    + "(debited|credited)\\s+with\\s+"
                    + "(?:Rs\\.?|INR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MERCHANT = Pattern.compile(
            "Merchant\\s*/\\s*Remarks:\\s*(.+?)(?:\\r?\\n|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE = Pattern.compile(
            "Date:\\s*\\w{3},\\s*(\\d{2}\\s+\\w{3}\\s+\\d{4}\\s+"
                    + "\\d{2}:\\d{2}:\\d{2})",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(RawMessage m) {
        return "email".equals(m.channel());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        String body = m.body();

        Matcher tx = TRANSACTION.matcher(body);
        if (!tx.find()) {
            return Optional.empty();
        }

        Direction direction =
                "debited".equalsIgnoreCase(tx.group(2))
                        ? Direction.DEBIT
                        : Direction.CREDIT;

        BigDecimal amount =
                new BigDecimal(tx.group(3).replace(",", ""))
                        .setScale(2);

        Matcher merchantMatcher = MERCHANT.matcher(body);
        String merchant = merchantMatcher.find()
                ? merchantMatcher.group(1).trim()
                : "";

        Matcher dateMatcher = DATE.matcher(body);
        if (!dateMatcher.find()) {
            return Optional.empty();
        }

        OffsetDateTime occurredAt =
                Dates.emailDate(dateMatcher.group(1));

        if (occurredAt == null) {
            return Optional.empty();
        }

        return Optional.of(new ParsedTxn(
                tx.group(1),
                occurredAt,
                direction,
                amount,
                merchant,
                null,
                m.messageId()));
    }
}