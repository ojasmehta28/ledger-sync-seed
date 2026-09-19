package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IciciSmsParser implements MessageParser {

    public static final String SENDER = "VM-ICICIB-T";

    // Format 1:
    // Acct XX9075 is debited with INR 22.50 on 01/07/2026 10:22.
    // Info: UPI/VEGETABLE VENDOR. Avl Bal Rs.31,882.25
    private static final Pattern V1 = Pattern.compile(
            "Acct XX(?<acct>\\d{4}) is (?<dir>debited|credited) with .*? "
                    + "on (?<when>\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2})\\. "
                    + "Info: (?<merchant>[^.]+)\\.",
            Pattern.CASE_INSENSITIVE);

    // Format 2:
    // ICICI Bank Acct XX9075 Cr INR 18000.00 on 01-Aug-2026 21:14;
    // NEFT INWARD SELF ref no 896005189492. BalAvl Rs 65,352.47
    private static final Pattern V2 = Pattern.compile(
            "ICICI Bank Acct XX(?<acct>\\d{4}) "
                    + "(?<dir>Cr|Dr) "
                    + "(?:INR|Rs\\.?)\\s*"
                    + "(?<amount>[0-9,]+(?:\\.[0-9]{1,2})?) "
                    + "on (?<when>\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2}); "
                    + "(?<merchant>.*?)"
                    + "(?:\\.|\\s+BalAvl|\\s+Avl\\s*Bal|$)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(RawMessage m) {
        return "sms".equals(m.channel()) && SENDER.equals(m.sender());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {

        String body = m.body();

        // Try the original ICICI format first.
        Matcher v1 = V1.matcher(body);
        if (v1.find()) {

            BigDecimal amount = Amounts.first(body);
            OffsetDateTime at = Dates.ist(v1.group("when"));

            if (amount == null || at == null) {
                return Optional.empty();
            }

            Direction direction =
                    "debited".equalsIgnoreCase(v1.group("dir"))
                            ? Direction.DEBIT
                            : Direction.CREDIT;

            return Optional.of(new ParsedTxn(
                    v1.group("acct"),
                    at,
                    direction,
                    amount,
                    v1.group("merchant").trim(),
                    Amounts.statedBalance(body),
                    m.messageId()
            ));
        }

        // Try the newer/alternate ICICI format.
        Matcher v2 = V2.matcher(body);
        if (v2.find()) {

            BigDecimal amount = new BigDecimal(
                    v2.group("amount").replace(",", "")
            ).setScale(2);

            OffsetDateTime at = Dates.ist(v2.group("when"));

            if (at == null) {
                return Optional.empty();
            }

            Direction direction =
                    "Dr".equalsIgnoreCase(v2.group("dir"))
                            ? Direction.DEBIT
                            : Direction.CREDIT;

            String merchant = v2.group("merchant").trim();

            return Optional.of(new ParsedTxn(
                    v2.group("acct"),
                    at,
                    direction,
                    amount,
                    merchant,
                    Amounts.statedBalance(body),
                    m.messageId()
            ));
        }

        return Optional.empty();
    }
}