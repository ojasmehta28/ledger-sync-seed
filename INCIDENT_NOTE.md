# Production Incident — Five-Line Note

1. The incident showed a customer spend of ₹92,213.10 for a water-can transaction whose actual amount was ₹5.
2. The root cause was the amount parser selecting the available-balance value instead of the transaction amount.
3. The affected transaction pattern was messages containing a transaction amount followed by an available balance.
4. The parser was changed to identify transaction-specific amounts and ignore available-balance fields.
5. A regression test was added for the ₹5 transaction and the full Gradle test suite passes.