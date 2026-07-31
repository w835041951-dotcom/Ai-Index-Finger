---
name: "Continuous Repo Improvement"
description: "Start a sustained autonomous session that verifies the AiIndexFinger baseline, delivers distinct new features, and refines the supplied objective into measurable engineering work."
argument-hint: "Goal, minimum new features, constraints, acceptance criteria, priority, and time budget"
agent: "Repo Steward"
---
Treat the input supplied with this prompt as the primary product objective. First preserve its intent while rewriting it into a concise, testable engineering brief. Then run repeated evidence-driven improvement cycles for the stated time budget.

The working brief must identify:

- The user or workflow being improved.
- Current and desired behavior.
- Acceptance criteria that can be verified in this repository.
- Constraints, compatibility requirements, and explicit non-goals.
- The minimum number of distinct user-visible features requested, if any.
- The requested time budget and stop conditions.

Before selecting work, establish an evidence-backed capability baseline from production code and tests. Cross-check planning documents, but treat them as potentially stale. Reject a proposed feature when equivalent behavior already exists, and record the code or test evidence that disqualified it.

Use the `Prompt Critic` and `Repo Auditor` subagents for independent analysis. Verify their findings yourself and maintain a feature ledger containing each candidate, evidence that it is new, its user-visible acceptance criteria, implementation status, and focused validation. Implement one small coherent feature at a time, add or update focused tests, and validate immediately after each edit. Continue to the next evidence-backed feature while the session is active and the budget remains. Do not wait for approval for reversible in-scope changes; ask only when a blocking product decision or safety-sensitive action cannot be inferred.

Count a feature only when it creates a distinct user capability with its own entry point or observable behavior and can be accepted independently. Do not count bug fixes, refactors, tests, documentation, localization, or separate internal parts of one capability as additional features. Do not count partial implementations. If the requested minimum cannot be completed within the budget, preserve working validated changes and report the exact completed count instead of weakening this definition.

Every new user-facing feature must ship with English and Simplified Chinese resources, relevant accessibility semantics, focused tests, and backward-compatible handling of persisted data. Preserve the on-device, user-controlled automation model: do not add network access, silently execute automation in the background, or retain sensitive screen, clipboard, node-text, or input data unless the objective explicitly requires it and the user approves the privacy change.

At completion, return the improved prompt, the baseline and rejected duplicates, the feature ledger with the number of independently completed features, exact validation outcomes, compatibility and privacy implications, residual risks, device-only checks, the stop reason, and the best next task.