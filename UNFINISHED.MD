# Unfinished / Known Limitations

1. Task 0 friend feedback is pending until the requested feedback is received.
2. The current reconciliation still contains a small difference between expected evidence and normalized output; this is reported rather than hidden.
3. MongoDB category totals currently use application-side aggregation rather than a Mongo aggregation pipeline.
4. The 100K performance measurements are benchmark measurements from a temporary synthetic dataset and are not production latency guarantees.
5. Further production hardening would include concurrency testing, failure-injection tests and operational monitoring.