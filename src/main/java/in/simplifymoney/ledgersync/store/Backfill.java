package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.util.HashSet;
import java.util.Set;

/**
 * Moves everything already in the SQL store into the document store.
 *
 * The operation is safe to run repeatedly because the document store
 * performs idempotent saves.
 */
public final class Backfill {

    private final SqlLedgerStore source;
    private final DocumentStore target;

    public Backfill(SqlLedgerStore source, DocumentStore target) {
        this.source = source;
        this.target = target;
    }

    public Result run() {
        long read = 0;
        long written = 0;
        long skipped = 0;

        Set<String> seen = new HashSet<>();

        for (NormalizedTxn txn : source.all()) {
            read++;

            String identity = identity(txn);

            if (!seen.add(identity)) {
                skipped++;
                continue;
            }

            target.save(txn);
            written++;
        }

        return new Result(read, written, skipped);
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

    public record Result(long read, long written, long skipped) {}
}