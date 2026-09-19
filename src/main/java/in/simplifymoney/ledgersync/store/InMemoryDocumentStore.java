package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryDocumentStore implements DocumentStore {

    private final Map<String, NormalizedTxn> byIdentity = new LinkedHashMap<>();
    private final Map<String, String> messageToIdentity = new LinkedHashMap<>();

    @Override
    public List<NormalizedTxn> forAccountMonth(
            String accountLast4, YearMonth month) {

        return byIdentity.values().stream()
                .filter(t -> t.accountLast4().equals(accountLast4))
                .filter(t -> YearMonth.from(t.occurredAt()).equals(month))
                .sorted(Comparator.comparing(
                        NormalizedTxn::occurredAt).reversed())
                .toList();
    }

    @Override
    public Map<Category, BigDecimal> categoryTotals(String accountLast4) {
        Map<Category, BigDecimal> totals = new LinkedHashMap<>();

        for (NormalizedTxn t : byIdentity.values()) {
            if (!t.accountLast4().equals(accountLast4)) {
                continue;
            }

            totals.merge(
                    t.category(),
                    t.amount(),
                    BigDecimal::add);
        }

        return totals;
    }

    @Override
    public Optional<NormalizedTxn> byMessageId(String messageId) {
        String identity = messageToIdentity.get(messageId);

        if (identity == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(byIdentity.get(identity));
    }

    @Override
    public void save(NormalizedTxn txn) {
        String identity = identity(txn);

        // Idempotent: saving the same logical transaction again
        // must not create another document.
        byIdentity.putIfAbsent(identity, txn);

        String storedIdentity = identity;

        for (String messageId : txn.sourceMessageIds()) {
            messageToIdentity.putIfAbsent(messageId, storedIdentity);
        }
    }

    private String identity(NormalizedTxn t) {
        return t.accountLast4()
                + "|" + t.occurredAt()
                + "|" + t.direction()
                + "|" + t.amount()
                + "|" + normalize(t.merchant());
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase();
    }
}