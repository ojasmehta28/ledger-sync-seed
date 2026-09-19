package in.simplifymoney.ledgersync.report;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class Reports {

    private Reports() {}

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public static Map<String, Object> summary(List<NormalizedTxn> ledger) {
        Map<String, Object> accounts = new LinkedHashMap<>();

        for (String acct : new TreeSet<>(
                ledger.stream()
                        .map(NormalizedTxn::accountLast4)
                        .toList())) {

            BigDecimal spend = ZERO;
            BigDecimal income = ZERO;
            BigDecimal microTotal = ZERO;
            BigDecimal transferredOut = ZERO;
            BigDecimal transferredIn = ZERO;
            int microCount = 0;

            for (NormalizedTxn t : ledger) {
                if (!t.accountLast4().equals(acct)) {
                    continue;
                }

                switch (t.category()) {
                    case SPEND -> {
                        if (t.direction() == Direction.DEBIT) {
                            spend = spend.add(t.amount());
                        }
                    }

                    case INCOME -> {
                        if (t.direction() == Direction.CREDIT) {
                            income = income.add(t.amount());
                        }
                    }

                    case MICRO -> {
                        microCount++;
                        microTotal = microTotal.add(t.amount());
                    }

                    case TRANSFER -> {
                        if (t.direction() == Direction.DEBIT) {
                            transferredOut = transferredOut.add(t.amount());
                        } else {
                            transferredIn = transferredIn.add(t.amount());
                        }
                    }
                }
            }

            Map<String, Object> account = new LinkedHashMap<>();
            account.put("spend", spend.toPlainString());
            account.put("income", income.toPlainString());
            account.put("micro_count", microCount);
            account.put("micro_total", microTotal.toPlainString());
            account.put("transferred_out", transferredOut.toPlainString());
            account.put("transferred_in", transferredIn.toPlainString());

            accounts.put(acct, account);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", accounts);
        return result;
    }

    public static Map<String, Object> ledgerDocument(
            List<NormalizedTxn> ledger) {

        List<Object> rows = ledger.stream().map(t -> {
            Map<String, Object> r = new LinkedHashMap<>();

            r.put("account_last4", t.accountLast4());
            r.put("occurred_at", t.occurredAt().toString());
            r.put("direction", t.direction().name().toLowerCase());
            r.put("amount", t.amount().toPlainString());
            r.put("category", t.category().name());
            r.put("merchant", t.merchant());
            r.put("source_message_ids", t.sourceMessageIds());

            return (Object) r;
        }).toList();

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("transactions", rows);
        return doc;
    }

    public static Map<String, Object> reconciliation(
            List<NormalizedTxn> ledger) {

        Map<String, Object> accounts = new LinkedHashMap<>();

        for (String acct : new TreeSet<>(
                ledger.stream()
                        .map(NormalizedTxn::accountLast4)
                        .toList())) {

            BigDecimal credits = ZERO;
            BigDecimal debits = ZERO;

            for (NormalizedTxn t : ledger) {
                if (!t.accountLast4().equals(acct)) {
                    continue;
                }

                if (t.direction() == Direction.CREDIT) {
                    credits = credits.add(t.amount());
                } else {
                    debits = debits.add(t.amount());
                }
            }

            Map<String, Object> account = new LinkedHashMap<>();
            account.put("account_last4", acct);
            account.put("credits", credits.toPlainString());
            account.put("debits", debits.toPlainString());

            /*
             * We do not invent an opening balance here.
             * The current report API receives only the normalized ledger,
             * not the bank's opening/closing balance metadata.
             *
             * Therefore this report exposes the movement totals that can
             * actually be derived from the ledger.
             */
            account.put(
                    "net_change",
                    credits.subtract(debits).setScale(2).toPlainString()
            );

            accounts.put(acct, account);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", accounts);

        return result;
    }

    public static Map<Category, BigDecimal> byCategory(
            List<NormalizedTxn> ledger) {

        Map<Category, BigDecimal> out = new LinkedHashMap<>();

        for (Category c : Category.values()) {
            out.put(c, ZERO);
        }

        for (NormalizedTxn t : ledger) {
            out.put(
                    t.category(),
                    out.get(t.category()).add(t.amount())
            );
        }

        return out;
    }
}