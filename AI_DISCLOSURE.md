# AI Disclosure

AI assistance was used during implementation for:

- debugging Java compilation and test failures
- explaining Java, Gradle and MongoDB concepts
- reviewing implementation approaches
- drafting documentation

A concrete example where AI output required correction was the transaction amount parser.

The initial generic amount extraction could select an available balance instead of the actual transaction amount. In the incident case, the real transaction was ₹5 while the available balance was ₹92,213.10.

The implementation was corrected to identify transaction-specific amount fields and ignore available-balance fields. A regression test was added to prevent the incident from recurring.

All retained implementation changes were tested locally with the project test suite and MongoDB execution checks.