# Ledger Sync

A small Java ledger pipeline that ingests SMS/email messages, normalizes real financial transactions, reconciles duplicate evidence, produces ledger/summary/reconciliation outputs, and backfills the normalized ledger into MongoDB.

## Stack

- Java 21
- Gradle
- H2
- MongoDB 7
- Docker Compose
- JUnit 5
- MongoDB Java Driver

## Run

Start MongoDB:

    docker compose up -d

Run tests:

    gradle test

Run the application:

    gradle run --args="migrate"

Ingest the supplied corpus:

    gradle run --args="ingest data/corpus.jsonl"

Generate reports:

    gradle run --args="report build/output"

Backfill the SQL ledger into MongoDB:

    gradle run --args="mongo-backfill"

The MongoDB defaults are:

    mongodb://localhost:27017
    database: ledger_sync

They can be overridden with MONGO_URI and MONGO_DATABASE.

## Architecture

The pipeline is split into:

1. Parsers
   - SMS and email parsers extract transaction evidence.
   - Non-transaction messages are ignored.

2. Ingestion
   - Parsed evidence is correlated using account, timestamp, direction, amount and normalized merchant identity.
   - Duplicate evidence is merged rather than creating duplicate ledger entries.
   - Transactions are categorized as SPEND, INCOME, MICRO or TRANSFER.

3. SQL ledger
   - H2 stores the normalized ledger.
   - The SQL ledger remains the source used for reporting and reconciliation.

4. Document store
   - MongoDB stores normalized transaction documents.
   - Indexes support the three required access patterns:
     - account + month + newest first
     - account category totals access
     - message ID lookup

5. Consistency checking
   - SQL and document-store records are compared on identity, timestamp, direction, amount, category, merchant and source-message evidence.

## Document model

Each MongoDB transaction contains:

- identity
- accountLast4
- occurredAt
- yearMonth
- direction
- amount
- category
- merchant
- sourceMessageIds

The identity is deterministic from the transaction's account, timestamp, direction, amount and normalized merchant.

This allows the backfill to be rerun without intentionally creating duplicate transaction documents.

## Performance measurements

The assignment requested measurements at 100,000 transactions.

The benchmark dataset was generated temporarily in MongoDB and removed after measurement. The production ledger remains at 265 normalized transactions.

| Query | Returned | Documents examined |
|---|---:|---:|
| Q1: account + month, newest first | 16,667 | 16,769 |
| Q2: account category-total access | 50,000 | 50,150 |
| Q3: message ID lookup | 1 | 1 |

### Interpretation

Q1 uses the compound index:

    accountLast4 + yearMonth + occurredAt(desc)

This supports both filtering and the requested newest-first ordering.

Q2 uses the account index to restrict the scan to the requested account.

Q3 uses the sourceMessageIds index and examined exactly one document for the benchmark lookup.

The small difference between returned and examined in Q1/Q2 is due to the temporary benchmark marker filter used while measuring the 100K dataset.

## Backfill and idempotency

The backfill reads the normalized SQL ledger and writes deterministic transaction identities into the document store.

Duplicate historical evidence is deduplicated before writing.

Repeated backfill runs therefore do not intentionally create another document for the same transaction identity.

## Reconciliation

The generated reconciliation report compares the expected transaction evidence with the normalized ledger.

The corpus contains evidence that does not map one-to-one to final ledger rows, including duplicate and non-transaction messages. The implementation reports these differences rather than manufacturing values to make totals match.

## Key decisions

### 1. Parse transaction-specific amount fields

Amount extraction was restricted to transaction patterns instead of taking the first currency-looking number in a message.

This was important because some messages contain an available balance after the transaction amount.

### 2. Ignore available-balance values

The parser explicitly avoids amount candidates associated with fields such as `Avl Bal`, `Available Balance`, `Bal Avl` and similar balance markers.

This prevents a balance from becoming the transaction amount.

### 3. Use message correlation

SMS and email evidence can represent the same underlying transaction. Correlation prevents multiple evidence records from becoming duplicate ledger transactions.

### 4. Keep source message IDs

Normalized transactions retain source message IDs so a ledger entry can be traced back to the evidence that produced it.

### 5. Separate transfers from spending

Transfers between accounts should not inflate spend totals, so transfer transactions are represented separately from SPEND and INCOME.

### 6. Keep MICRO separate

Small UPI-style debits are classified separately as MICRO rather than silently adding them to regular spend.

### 7. Deterministic document identity

The document-store identity is deterministic so repeated backfills can target the same document.

### 8. Compound index for the primary account/month query

The account/month/time query is common enough to justify a compound index that also satisfies newest-first ordering.

### 9. Validate SQL and document stores independently

A consistency checker compares the two representations instead of assuming that a successful write means the stores are equivalent.

### 10. Prefer honest reconciliation

When source evidence and final ledger counts differ, the difference is reported instead of changing data solely to make a checkpoint match.

## AI disclosure

AI assistance was used during implementation for debugging, code review, explanation of Java/MongoDB concepts, and drafting documentation.

One concrete example of where AI output required correction was the transaction amount parsing: a generic "first currency amount" approach selected an available-balance value (`Rs.92,213.10`) instead of the actual transaction amount (`Rs.5`). The implementation was corrected to identify transaction-specific amount fields and ignore balance fields, and a regression test was added.

AI-generated suggestions were reviewed and tested locally before being retained.

## Unfinished / known limitations

- Task 0 friend feedback is pending until the requested feedback is received.
- The current reconciliation still contains a small difference between expected evidence and normalized output; this is reported rather than hidden.
- The Mongo implementation currently uses application-side aggregation for category totals rather than a Mongo aggregation pipeline.
- The benchmark numbers are measured MongoDB execution statistics on a temporary 100K dataset and are not production latency guarantees.
- Further production hardening would include additional concurrency testing, failure-injection tests and operational monitoring.