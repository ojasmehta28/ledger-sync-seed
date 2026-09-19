# Decision Log

## 1. Transaction-specific amount extraction

**Decision:** Extract the amount associated with an explicit debit/credit transaction field instead of taking the first currency-looking number.

**Evidence:** The corpus contains messages where the actual transaction amount is followed by an available balance.

**Why:** A generic first-number parser can turn a balance into the transaction amount.

---

## 2. Ignore available-balance values

**Decision:** Ignore amount candidates associated with `Avl Bal`, `Available Balance`, `Bal Avl` and similar balance markers.

**Evidence:** The incident message contained an actual transaction of ₹5 followed by an available balance of ₹92,213.10.

**Why:** The available balance is account state, not transaction value.

---

## 3. Preserve source message IDs

**Decision:** Store the source message IDs on normalized transactions.

**Evidence:** Multiple SMS/email messages can represent the same underlying transaction.

**Why:** Source IDs provide traceability and allow a ledger entry to be traced back to its evidence.

---

## 4. Correlate duplicate evidence

**Decision:** Correlate parsed evidence using account, timestamp, direction, amount and normalized merchant identity.

**Evidence:** The corpus contains duplicate evidence for some transactions.

**Why:** Without correlation, the same financial event can appear multiple times in the ledger.

---

## 5. Separate transfers from spending

**Decision:** Classify identifiable account-to-account/self transfers as `TRANSFER`.

**Evidence:** Transfer activity exists in the corpus and should not be interpreted as customer spending.

**Why:** Treating transfers as spend would distort spending totals.

---

## 6. Keep MICRO transactions separate

**Decision:** Keep qualifying small UPI transactions in the `MICRO` category.

**Evidence:** The corpus contains small UPI debits.

**Why:** This preserves the requested category distinction instead of silently folding these transactions into regular spend.

---

## 7. Use deterministic document identity

**Decision:** Generate a deterministic identity from account, timestamp, direction, amount and normalized merchant.

**Evidence:** Backfill must be safely repeatable.

**Why:** A deterministic identity allows repeated writes to target the same logical transaction.

---

## 8. Use a compound MongoDB index

**Decision:** Index `(accountLast4, yearMonth, occurredAt desc)`.

**Evidence:** Q1 requires transactions for an account/month in newest-first order.

**Why:** The index supports both filtering and the requested ordering.

---

## 9. Keep message ID lookup indexed

**Decision:** Index `sourceMessageIds`.

**Evidence:** Q3 requires lookup of a transaction from a message ID.

**Why:** The measured 100K benchmark examined 1 document and returned 1 document for the message lookup.

---

## 10. Report reconciliation differences honestly

**Decision:** Do not alter ledger data solely to make checkpoint totals match.

**Evidence:** The corpus contains duplicate and non-transaction evidence, and the normalized output does not perfectly match every expected checkpoint.

**Why:** The assignment explicitly values honest reconciliation over manufactured matching numbers.