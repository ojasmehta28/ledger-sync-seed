package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proves the two stores agree, and reports precise differences.
 */
public final class ConsistencyChecker {

    private final SqlLedgerStore sql;
    private final DocumentStore documents;

    public ConsistencyChecker(SqlLedgerStore sql, DocumentStore documents) {
        this.sql = sql;
        this.documents = documents;
    }

    public List<Divergence> check() {
        List<Divergence> divergences = new ArrayList<>();

        Map<String, NormalizedTxn> sqlByIdentity = index(sql.all());
        Map<String, NormalizedTxn> documentByIdentity = index(allDocuments());

        for (Map.Entry<String, NormalizedTxn> entry : sqlByIdentity.entrySet()) {
            String identity = entry.getKey();
            NormalizedTxn sqlTxn = entry.getValue();
            NormalizedTxn documentTxn = documentByIdentity.get(identity);

            if (documentTxn == null) {
                divergences.add(new Divergence(
                        "missing transaction: " + identity,
                        describe(sqlTxn),
                        "<missing>"));
                continue;
            }

            compareFields(identity, sqlTxn, documentTxn, divergences);
        }

        for (Map.Entry<String, NormalizedTxn> entry : documentByIdentity.entrySet()) {
            if (!sqlByIdentity.containsKey(entry.getKey())) {
                divergences.add(new Divergence(
                        "extra transaction: " + entry.getKey(),
                        "<missing>",
                        describe(entry.getValue())));
            }
        }

        return divergences;
    }

    private void compareFields(
            String identity,
            NormalizedTxn sqlTxn,
            NormalizedTxn documentTxn,
            List<Divergence> divergences) {

        if (!sqlTxn.occurredAt().equals(documentTxn.occurredAt())) {
            divergences.add(new Divergence(
                    identity + ".occurredAt",
                    sqlTxn.occurredAt().toString(),
                    documentTxn.occurredAt().toString()));
        }

        if (sqlTxn.direction() != documentTxn.direction()) {
            divergences.add(new Divergence(
                    identity + ".direction",
                    sqlTxn.direction().name(),
                    documentTxn.direction().name()));
        }

        if (sqlTxn.amount().compareTo(documentTxn.amount()) != 0) {
            divergences.add(new Divergence(
                    identity + ".amount",
                    sqlTxn.amount().toPlainString(),
                    documentTxn.amount().toPlainString()));
        }

        if (sqlTxn.category() != documentTxn.category()) {
            divergences.add(new Divergence(
                    identity + ".category",
                    sqlTxn.category().name(),
                    documentTxn.category().name()));
        }

        if (!sqlTxn.merchant().equals(documentTxn.merchant())) {
            divergences.add(new Divergence(
                    identity + ".merchant",
                    sqlTxn.merchant(),
                    documentTxn.merchant()));
        }

        List<String> sqlMessages = sqlTxn.sourceMessageIds().stream()
                .sorted()
                .toList();

        List<String> documentMessages = documentTxn.sourceMessageIds().stream()
                .sorted()
                .toList();

        if (!sqlMessages.equals(documentMessages)) {
            divergences.add(new Divergence(
                    identity + ".sourceMessageIds",
                    sqlMessages.toString(),
                    documentMessages.toString()));
        }
    }

    /**
     * The in-memory implementation keeps the complete document set private.
     * This method currently obtains it through the supported account/month
     * access pattern.
     */
    private List<NormalizedTxn> allDocuments() {
        Map<String, NormalizedTxn> all = new HashMap<>();

        for (NormalizedTxn sqlTxn : sql.all()) {
            for (NormalizedTxn documentTxn :
                    documents.forAccountMonth(
                            sqlTxn.accountLast4(),
                            java.time.YearMonth.from(sqlTxn.occurredAt()))) {

                all.put(identity(documentTxn), documentTxn);
            }
        }

        return new ArrayList<>(all.values());
    }

    private Map<String, NormalizedTxn> index(List<NormalizedTxn> transactions) {
        Map<String, NormalizedTxn> result = new HashMap<>();

        for (NormalizedTxn txn : transactions) {
            result.put(identity(txn), txn);
        }

        return result;
    }

    private String identity(NormalizedTxn txn) {
    return txn.accountLast4()
            + "|"
            + txn.occurredAt()
            + "|"
            + txn.direction()
            + "|"
            + normalize(txn.merchant());
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase();
    }

    private String describe(NormalizedTxn txn) {
        return "account=" + txn.accountLast4()
                + ", occurredAt=" + txn.occurredAt()
                + ", direction=" + txn.direction()
                + ", amount=" + txn.amount()
                + ", category=" + txn.category()
                + ", merchant=" + txn.merchant()
                + ", sourceMessageIds=" + txn.sourceMessageIds();
    }

    public record Divergence(
            String what,
            String inSql,
            String inDocuments) {}
}