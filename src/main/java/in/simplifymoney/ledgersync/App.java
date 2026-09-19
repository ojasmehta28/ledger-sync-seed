package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.report.Reports;
import in.simplifymoney.ledgersync.store.Backfill;
import in.simplifymoney.ledgersync.store.MongoDocumentStore;
import in.simplifymoney.ledgersync.store.SqlLedgerStore;

import java.nio.file.Files;
import java.nio.file.Path;

public final class App {

    private static final Path DB = Path.of("data", "ledger");
    private static final Path MIGRATIONS = Path.of("db", "migration");

    private static final String MONGO_URI =
            System.getenv().getOrDefault(
                    "MONGO_URI",
                    "mongodb://localhost:27017"
            );

    private static final String MONGO_DATABASE =
            System.getenv().getOrDefault(
                    "MONGO_DATABASE",
                    "ledger_sync"
            );

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.err.println(
                    "usage: migrate | ingest <corpus.jsonl> | report <out-dir> | mongo-backfill"
            );
            System.exit(2);
        }

        Files.createDirectories(DB.getParent());

        switch (args[0]) {

            case "migrate" -> {
                try (SqlLedgerStore store = new SqlLedgerStore(DB)) {
                    store.migrate(MIGRATIONS);
                    System.out.println("ledger rows: " + store.count());
                }
            }

            case "ingest" -> {
                if (args.length < 2) {
                    throw new IllegalArgumentException(
                            "ingest needs a corpus"
                    );
                }

                try (SqlLedgerStore store = new SqlLedgerStore(DB)) {

                    store.migrate(MIGRATIONS);

                    var stats = new IngestService(
                            new Parsers(),
                            store
                    ).ingestFile(Path.of(args[1]));

                    System.out.println(stats);
                    System.out.println(
                            "ledger rows: " + store.count()
                    );
                }
            }

            case "report" -> {
                if (args.length < 2) {
                    throw new IllegalArgumentException(
                            "report needs a directory"
                    );
                }

                Path out = Path.of(args[1]);
                Files.createDirectories(out);

                try (SqlLedgerStore store =
                             new SqlLedgerStore(DB)) {

                    var ledger = store.all();

                    Files.writeString(
                            out.resolve("ledger.json"),
                            Json.writePretty(
                                    Reports.ledgerDocument(ledger)
                            )
                    );

                    Files.writeString(
                            out.resolve("summary.json"),
                            Json.writePretty(
                                    Reports.summary(ledger)
                            )
                    );

                    Files.writeString(
                            out.resolve("reconciliation.json"),
                            Json.writePretty(
                                    Reports.reconciliation(ledger)
                            )
                    );

                    System.out.println(
                            "wrote 3 files to " + out
                    );
                }
            }

            case "mongo-backfill" -> {

                try (SqlLedgerStore sql =
                             new SqlLedgerStore(DB);
                     MongoDocumentStore mongo =
                             new MongoDocumentStore(
                                     MONGO_URI,
                                     MONGO_DATABASE
                             )) {

                    sql.migrate(MIGRATIONS);

                    Backfill backfill =
                            new Backfill(sql, mongo);

                    Backfill.Result result =
                            backfill.run();

                    System.out.println(
                            "Mongo backfill complete"
                    );

                    System.out.println(
                            "rows read: " + result.read()
                    );

                    System.out.println(
                            "documents written: "
                                    + result.written()
                    );

                    System.out.println(
                            "duplicates skipped: "
                                    + result.skipped()
                    );
                }
            }

            default -> {
                System.err.println(
                        "unknown command: " + args[0]
                );
                System.exit(2);
            }
        }
    }
}