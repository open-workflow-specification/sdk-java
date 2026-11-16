Report only defects introduced by this pull request or pre-existing defects whose behavior this pull request changes, newly exposes, or relies on. Do not report unrelated pre-existing issues in unchanged code. When raising a finding, identify the changed code that introduces or exposes it; if there is no such connection, omit it.

Prioritize findings by demonstrated impact, not by theoretical possibility. Before reporting an issue, trace how it affects a user, workflow, process, or resource under a realistic supported configuration. Explain that impact in the finding.

Use HIGH only for a serious, realistic problem such as a security vulnerability, data loss, a major correctness failure, or substantial availability/performance impact. Do not mark an issue HIGH merely because code throws or behaves unexpectedly.

In particular, an exception captured by a CompletableFuture that is intentionally ignored is not itself a user-visible failure. Report it only if you can identify a separate consequence, such as a leaked process, blocked thread, unbounded resource growth, or failed workflow.

For lower-impact issues, use a lower severity. Omit speculative, low-confidence, or purely stylistic findings unless they are specifically requested. Prefer a small number of well-supported findings over exhaustive lists of edge cases.

Also try to collect all issues in one shot, to avoid an endless loop of reviews and avoid reporting formatting, unit test and integration test issues that are already detected by regular PR checks. 
