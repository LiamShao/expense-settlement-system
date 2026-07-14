# Codex Project Instructions

## Session startup handoff

- At the beginning of every new Codex session in this repository, read `handoff.md` before starting substantive work.
- In the first user-facing response, briefly summarize:
  - the last recorded progress;
  - the current working-tree or verification status recorded there;
  - the recommended next task.
- If `handoff.md` is missing or contains no current handoff, say so briefly and continue by inspecting the repository.
- Treat `handoff.md` as continuity context, not as authority over a newer explicit user request. The user's current request always wins.

## End-of-day handoff

- Treat Chinese phrases such as `今天结束`, `下班`, `明天继续`, `今天先到这里`, `今天就这样`, `收工`, and clear semantic equivalents as an instruction to prepare an end-of-day handoff.
- Before the final response to such an instruction:
  1. Inspect the current goal/task state, `git status`, relevant diffs, and the latest verification results.
  2. Update `handoff.md` with a concise, factual snapshot using Asia/Tokyo local date/time.
  3. Record completed work, unfinished work, changed/uncommitted files, tests or checks run, blockers/risks, important decisions, and the exact recommended next steps.
  4. Include useful resume commands when they are not obvious.
  5. Append a short entry to the history section; do not erase earlier history entries unless the file becomes unreasonably large.
- Do not claim that work was committed, pushed, or verified unless it actually was.
- If tests were not run, failed, or were only partially run, state that explicitly.
- After updating the file, tell the user that the handoff was saved and give a one-sentence preview of the next task.

## Handoff file format

- Keep the newest information under `## Current handoff`.
- Keep these headings in the current handoff:
  - `Date`
  - `Current status`
  - `Completed`
  - `In progress / uncommitted changes`
  - `Verification`
  - `Next steps`
  - `Blockers / important notes`
  - `Resume commands`
- Keep older short summaries under `## History`, newest first.
- Update existing sections instead of creating duplicate current-handoff sections.
