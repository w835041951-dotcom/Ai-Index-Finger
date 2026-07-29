---
name: "Continuous Repo Improvement"
description: "Start a sustained autonomous session that improves AiIndexFinger functionality and tests while refining the supplied user prompt into measurable engineering work."
argument-hint: "Goal, current problem, constraints, acceptance criteria, priority, and time budget"
agent: "Repo Steward"
---
Treat the input supplied with this prompt as the primary product objective. First preserve its intent while rewriting it into a concise, testable engineering brief. Then run repeated evidence-driven improvement cycles for the stated time budget.

The working brief must identify:

- The user or workflow being improved.
- Current and desired behavior.
- Acceptance criteria that can be verified in this repository.
- Constraints, compatibility requirements, and explicit non-goals.
- The requested time budget and stop conditions.

Use the `Prompt Critic` and `Repo Auditor` subagents for independent analysis. Verify their findings yourself, implement one small coherent change at a time, add or update focused tests, and validate immediately after each edit. Continue to the next evidence-backed improvement while the session is active and the budget remains. Do not wait for approval for reversible in-scope changes; ask only when a blocking product decision or safety-sensitive action cannot be inferred.

At completion, return the improved prompt, each implemented change, exact validation outcomes, residual risks, and the best next task.