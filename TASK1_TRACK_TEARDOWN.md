# Task 1 — Track Flow Teardown

## Flow tested

I tested the Track flow in the Simplify Money application.

The flow included:

1. Opening the Track section.
2. Selecting `+ Track Account`.
3. Reviewing the available account/data-source options.
4. Opening the bank/account connection flow.
5. Reviewing the transaction list after tracking data.

Screenshots from the flow are included with the submission.

## What worked well

- The Track section is easy to locate.
- The primary `+ Track Account` action is clearly visible.
- The account connection flow makes the available data-source choices visible.
- The resulting transaction list provides useful transaction-level information.

## Where I trusted the result

I trusted transactions when the merchant, amount and transaction direction were consistent with the source evidence.

## Where I did not fully trust the result

I found that transaction categorization and transaction recognition should be treated carefully because financial messages can contain multiple monetary values, including balances that are not transaction amounts.

This was especially relevant to the production incident in the backend assignment.

## Three changes I would make

### 1. Add clearer transaction-source traceability

Show the source or reason behind a transaction when confidence is low.

**Why:** Financial users need to understand where a transaction came from when something looks incorrect.

### 2. Improve category correction

Allow users to easily correct an incorrect transaction category.

**Why:** Categorization can be imperfect and user correction provides a direct way to improve trust.

### 3. Highlight unusual or uncertain transactions

Provide a subtle indicator when a transaction requires review.

**Why:** This lets users focus attention on potentially incorrect transactions instead of manually checking every transaction.

## Screenshots

The submission includes screenshots of:

- Track landing screen
- Track account/data-source selection
- Tracked transaction list