package in.simplifymoney.ledgersync.store;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import org.bson.Document;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MongoDocumentStore implements DocumentStore, AutoCloseable {

    private final MongoClient client;
    private final MongoCollection<Document> transactions;

    public MongoDocumentStore(String uri, String databaseName) {
        this.client = MongoClients.create(uri);

        MongoDatabase database = client.getDatabase(databaseName);
        this.transactions = database.getCollection("transactions");

        createIndexes();
    }

    private void createIndexes() {
        transactions.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("accountLast4"),
                        Indexes.ascending("yearMonth"),
                        Indexes.descending("occurredAt")
                )
        );

        transactions.createIndex(
                Indexes.ascending("accountLast4")
        );

        transactions.createIndex(
                Indexes.ascending("sourceMessageIds")
        );

        transactions.createIndex(
        Indexes.ascending("identity"),
        new com.mongodb.client.model.IndexOptions().unique(true)
        );
    }

    @Override
    public List<NormalizedTxn> forAccountMonth(
            String accountLast4,
            YearMonth month) {

        List<NormalizedTxn> result = new ArrayList<>();

        transactions.find(
                Filters.and(
                        Filters.eq("accountLast4", accountLast4),
                        Filters.eq("yearMonth", month.toString())
                )
        )
        .sort(Indexes.descending("occurredAt"))
        .forEach(document -> result.add(fromDocument(document)));

        return result;
    }

    @Override
    public Map<Category, BigDecimal> categoryTotals(String accountLast4) {

        Map<Category, BigDecimal> totals = new LinkedHashMap<>();

        for (Document document :
                transactions.find(Filters.eq("accountLast4", accountLast4))) {

            NormalizedTxn txn = fromDocument(document);

            totals.merge(
                    txn.category(),
                    txn.amount(),
                    BigDecimal::add
            );
        }

        return totals;
    }

    @Override
    public Optional<NormalizedTxn> byMessageId(String messageId) {

        Document document = transactions.find(
                Filters.eq("sourceMessageIds", messageId)
        ).first();

        if (document == null) {
            return Optional.empty();
        }

        return Optional.of(fromDocument(document));
    }

    @Override
    public void save(NormalizedTxn txn) {

        String identity = identity(txn);

        Document document = toDocument(txn);

        transactions.replaceOne(
                Filters.eq("identity", identity),
                document,
                new ReplaceOptions().upsert(true)
        );
    }

    private Document toDocument(NormalizedTxn txn) {

        return new Document()
                .append("identity", identity(txn))
                .append("accountLast4", txn.accountLast4())
                .append("occurredAt", txn.occurredAt().toString())
                .append("yearMonth",
                        YearMonth.from(txn.occurredAt()).toString())
                .append("direction", txn.direction().name())
                .append("amount", txn.amount().toPlainString())
                .append("category", txn.category().name())
                .append("merchant", txn.merchant())
                .append("sourceMessageIds",
                        txn.sourceMessageIds());
    }

    @SuppressWarnings("unchecked")
    private NormalizedTxn fromDocument(Document document) {

        return new NormalizedTxn(
                document.getString("accountLast4"),
                java.time.OffsetDateTime.parse(
                        document.getString("occurredAt")
                ),
                Direction.valueOf(
                        document.getString("direction")
                ),
                new BigDecimal(
                        document.getString("amount")
                ),
                Category.valueOf(
                        document.getString("category")
                ),
                document.getString("merchant"),
                ((List<String>) document.get("sourceMessageIds"))
        );
    }

    private String identity(NormalizedTxn txn) {
        return txn.accountLast4()
                + "|" + txn.occurredAt()
                + "|" + txn.direction()
                + "|" + txn.amount()
                + "|" + normalize(txn.merchant());
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase();
    }

    public void close() {
        client.close();
    }
}