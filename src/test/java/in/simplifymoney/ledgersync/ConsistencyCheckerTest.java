package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.store.ConsistencyChecker;
import in.simplifymoney.ledgersync.store.InMemoryDocumentStore;
import in.simplifymoney.ledgersync.store.SqlLedgerStore;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConsistencyCheckerTest {

    @Test
    void detectsChangedAmount() throws Exception {
        Path db = Files.createTempFile("ledger-check-", "");

        try (SqlLedgerStore sql = new SqlLedgerStore(db)) {
            sql.migrate(Path.of("db/migration"));

            NormalizedTxn sqlTxn = txn(new BigDecimal("100.00"));

            sql.save(sqlTxn);

            InMemoryDocumentStore documents = new InMemoryDocumentStore();

            NormalizedTxn changed = txn(new BigDecimal("200.00"));
            documents.save(changed);

            ConsistencyChecker checker =
                    new ConsistencyChecker(sql, documents);

            List<ConsistencyChecker.Divergence> divergences =
                    checker.check();

            assertTrue(
                    divergences.stream()
                            .anyMatch(d -> d.what().contains(".amount")),
                    "checker should report an amount divergence");
        } finally {
            Files.deleteIfExists(db);
            Files.deleteIfExists(Path.of(db + ".mv.db"));
        }
    }

    private NormalizedTxn txn(BigDecimal amount) {
        return new NormalizedTxn(
                "4821",
                OffsetDateTime.parse("2026-07-04T11:54:00+05:30"),
                Direction.DEBIT,
                amount,
                Category.SPEND,
                "UPI/WATER CAN",
                List.of("test-message-1"));
    }
}