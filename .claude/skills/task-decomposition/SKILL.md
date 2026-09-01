---
name: task-decomposition
description: How to break any TASKS.md entry into implementable subtasks before writing code. Defines subtask size limits, the mandatory "done when" check, and extra scrutiny for security-relevant seams (auth, RBAC, SSH/execution). Read before starting any ⚙️/🎨 task in TASKS.md, and before backend-agent or frontend-agent begins implementation.
---

# Task decomposition (all tasks)

Every task in TASKS.md gets broken into subtasks before any subagent touches
code. This is not the same step as "write a plan" — a plan describes intent;
a subtask list is a queue of implementable, individually-checkable units. See
the Authorization (task #15) breakdown for a worked example of this applied
end to end.

## Why

The project already has a hard rule: *never generate large chunks of
application code in one shot.* That's not a vibe, it's a size limit on what
one subtask is allowed to touch. If a subtask can't be scoped small enough to
obey that rule, it isn't a subtask yet — it's still a task, and it needs to be
split further before any code gets written.

## What counts as one subtask

A subtask is sized correctly when ALL of the following hold:

- It touches **one file**, or **one method/function** in an existing file, in
  one commit. A second file is allowed only if it exists solely to support the
  first (e.g. a new DTO used by a new endpoint) — never two behavioral changes
  in one subtask.
- It has exactly **one reason to fail**. If the verification could fail for
  two unrelated causes, split it.
- It's reviewable by a human or qa-reviewer in **under a minute**.
- It ends in a concrete, checkable **"done when"** — a curl command, a
  specific test assertion, a specific manual check. Never "and then test
  everything."

If a piece of work doesn't meet all four, decompose it further. Don't bundle
"just this once."

## The 🔒 marker: extra scrutiny

Flag a subtask 🔒 if it does any of the following. It still obeys the size
rule above — the flag changes how carefully it's reviewed, not how big it's
allowed to be.

- Moves code from *parses/validates a credential* to *authenticates a
  request* (e.g. populating `SecurityContext`, not just extracting a header).
- Adds or changes a `@PreAuthorize` / `authorizeHttpRequests` matcher — i.e.
  anything that changes who is allowed to call what.
- Handles credentials, tokens, or anything that could end up in a header, a
  log line, or an audit row.
- Touches the SSH/execution layer — already covered by the project's Security
  Reviewer sub-agent rule; this flag is for auth/RBAC, which isn't
  automatically covered by that rule but is just as high-stakes.

## Output format

Nest subtasks under the parent task's TASKS.md number (task #15 → `15.1`,
`15.2`, ...). Group by natural layer with a short subheading per group —
never present 15+ rows as one flat list.

| # | Subtask | Touches | Done when |
|---|---|---|---|

## Process

1. Read the task's TASKS.md entry and the relevant SPEC.md section(s).
2. List every file the task will eventually touch. If you can't list the
   files yet, the task isn't ready to decompose — go back to SPEC.md first.
3. For each file, ask: one pass, or two behaviorally distinct commits?
   Example: "extract the JWT claims" and "use those claims to populate
   SecurityContext" are two subtasks even though it's the same file and feels
   natural to write together — the second one is where *parsing* becomes
   *authenticating*, which is exactly the seam that needs its own review.
4. Write each subtask's "done when" **before** implementing it. If you can't
   state it concretely, the subtask is still too vague.
5. Mark 🔒 subtasks per the rule above.
6. Hand the list to the relevant subagent one subtask at a time — never the
   whole task at once. Each subtask is its own turn.

## Anti-patterns

- **"Wire up SecurityConfig" as one subtask.** SecurityConfig accumulates
  matchers per resource over the project's life; one matcher-group per
  resource is correct (engines, users, and audit-logs are three subtasks, not
  one).
- **Bundling the filter chain's parse step with its authenticate step.** This
  pair is the single highest-leverage bug in a JWT filter — always two
  subtasks.
- **"Add `@PreAuthorize` to the controllers"** covering multiple controllers
  or multiple verbs at once. One controller, sometimes one verb, per subtask.
- **A "done when" that says "tests pass."** That describes the whole task,
  not one subtask. Every "done when" should be something you could paste into
  a terminal or a test file right now.
- **Skipping decomposition because a task is "already small."** Write the
  one-row breakdown anyway, so the "done when" is stated explicitly before
  code exists.

## Where this fits in the four-step sequence

SPEC.md update → propose skills/hooks → **decompose into subtasks (this
skill)** → implement one subtask at a time. Subagents expect a subtask, not a
task, as their unit of work; if handed a whole task, they decompose it first
per this skill before writing any code.
