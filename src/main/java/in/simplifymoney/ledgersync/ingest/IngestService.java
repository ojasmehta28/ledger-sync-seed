package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.LedgerStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class IngestService {

    private final Parsers parsers;
    private final LedgerStore store;

    public IngestService(Parsers parsers, LedgerStore store) {
        this.parsers = parsers;
        this.store = store;
    }

    public Stats ingestFile(Path corpus) throws IOException {
        List<RawMessage> messages = readCorpus(corpus);

        int skipped = 0;
        Map<String, CorrelatedTxn> correlated = new LinkedHashMap<>();

        for (RawMessage m : messages) {
            Optional<ParsedTxn> parsed = parsers.parse(m);

            if (parsed.isEmpty()) {
                skipped++;
                continue;
            }

            ParsedTxn p = parsed.get();

            String fingerprint = fingerprint(p);

            CorrelatedTxn existing = correlated.get(fingerprint);

            if (existing == null) {
                correlated.put(
                        fingerprint,
                        new CorrelatedTxn(
                                p.accountLast4(),
                                p.occurredAt(),
                                p.direction(),
                                p.amount(),
                                category(p),
                                p.merchant(),
                                new ArrayList<>(List.of(p.sourceMessageId()))
                        )
                );
            } else {
    // if ("4821".equals(p.accountLast4())) {
    //     System.out.println(
    //             "CORRELATED 4821: "
    //                     + p.sourceMessageId()
    //                     + " -> existing "
    //                     + existing.sourceMessageIds
    //                     + " | "
    //                     + p.occurredAt()
    //                     + " | "
    //                     + p.direction()
    //                     + " | "
    //                     + p.amount()
    //                     + " | "
    //                     + p.merchant()
    //     );
    // }

    existing.sourceMessageIds.add(p.sourceMessageId());

                // Prefer a non-empty merchant if the first evidence lacked one.
                if ((existing.merchant == null || existing.merchant.isBlank())
                        && p.merchant() != null
                        && !p.merchant().isBlank()) {
                    existing.merchant = p.merchant();
                }
            }
        }

        List<CorrelatedTxn> ordered = new ArrayList<>(correlated.values());

        ordered.sort(Comparator
                .comparing((CorrelatedTxn t) -> t.occurredAt)
                .thenComparing(t -> t.accountLast4)
                .thenComparing(t -> t.direction.name())
                .thenComparing(t -> t.amount));

        for (CorrelatedTxn t : ordered) {
            t.sourceMessageIds.sort(String::compareTo);

            store.save(new NormalizedTxn(
                    t.accountLast4,
                    t.occurredAt,
                    t.direction,
                    t.amount,
                    t.category,
                    t.merchant,
                    t.sourceMessageIds
            ));
        }

        return new Stats(
                messages.size(),
                ordered.size(),
                skipped
        );
    }

    public static List<RawMessage> readCorpus(Path corpus) throws IOException {
        List<RawMessage> out = new ArrayList<>();

        try (Stream<String> lines = Files.lines(corpus)) {
            for (String line : (Iterable<String>) lines
                    .filter(s -> !s.isBlank())::iterator) {

                Map<String, Object> o = Json.parseObject(line);

                out.add(new RawMessage(
                        (String) o.get("message_id"),
                        (String) o.get("channel"),
                        (String) o.get("sender"),
                        OffsetDateTime.parse((String) o.get("received_at")),
                        (String) o.get("device_id"),
                        (String) o.get("body")
                ));
            }
        }

        return out;
    }

    private Category category(ParsedTxn p) {
        String merchant = p.merchant() == null
                ? ""
                : p.merchant().toUpperCase();

        if (isOwnTransfer(merchant)) {
            return Category.TRANSFER;
        }

        if (p.direction() == Direction.DEBIT
                && merchant.contains("UPI")
                && p.amount().compareTo(java.math.BigDecimal.valueOf(100)) <= 0) {
            return Category.MICRO;
        }

        return p.direction() == Direction.DEBIT
                ? Category.SPEND
                : Category.INCOME;
    }

    private boolean isOwnTransfer(String merchant) {
        String value = merchant.toUpperCase();

        return value.contains("SELF")
                || value.contains("OWN ACCOUNT")
                || value.contains("OWN A/C")
                || value.contains("SELF TRANSFER");
    }

    private String fingerprint(ParsedTxn p) {
        return String.join("|",
                p.accountLast4(),
                p.occurredAt().toString(),
                p.direction().name(),
                p.amount().toPlainString(),
                normalizeMerchant(p.merchant())
        );
    }

    private String normalizeMerchant(String merchant) {
        if (merchant == null) {
            return "";
        }

        return merchant
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase();
    }

    private static final class CorrelatedTxn {

        private final String accountLast4;
        private final OffsetDateTime occurredAt;
        private final Direction direction;
        private final java.math.BigDecimal amount;
        private final Category category;
        private String merchant;
        private final List<String> sourceMessageIds;

        private CorrelatedTxn(
                String accountLast4,
                OffsetDateTime occurredAt,
                Direction direction,
                java.math.BigDecimal amount,
                Category category,
                String merchant,
                List<String> sourceMessageIds) {

            this.accountLast4 = accountLast4;
            this.occurredAt = occurredAt;
            this.direction = direction;
            this.amount = amount;
            this.category = category;
            this.merchant = merchant;
            this.sourceMessageIds = sourceMessageIds;
        }
    }

    public record Stats(
            int messagesRead,
            int transactionsWritten,
            int messagesSkipped) {
    }
}