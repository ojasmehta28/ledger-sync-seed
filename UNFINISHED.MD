# Unfinished / Known Limitations

1. The current reconciliation still contains a small difference between expected evidence and normalized output; this is reported rather than hidden.
2. MongoDB category totals currently use application-side aggregation rather than a Mongo aggregation pipeline.
3. The 100K performance measurements are benchmark measurements from a temporary synthetic dataset and are not production latency guarantees.
4. Further production hardening would include concurrency testing, failure-injection tests and operational monitoring.