# ktsearch-cli agent guide

## Scope

These instructions apply to `ktsearch-cli/` only.

## Build and test guardrails

- Use the Gradle wrapper from repo root.
- Keep changes multiplatform-safe; avoid JVM-only APIs in `commonMain`.
- Before finishing, run at least:
  - `./gradlew :ktsearch-cli:assemble`
  - `./gradlew :ktsearch-cli:jvmTest`
- If you touch command help, flags, or command structure, regenerate and
  commit the manual by running:
  - `./gradlew :ktsearch-cli:jvmTest --tests '*CliManualTest*'`

## Documentation conventions

- Keep user-facing CLI docs aligned across:
  - `ktsearch-cli/README.md`
  - `ktsearch-cli/cli-manual.md`
- `cli-manual.md` is generated output. Do not hand-edit it.
- Prefer concise, copy/paste-ready examples that use real `ktsearch`
  commands.
- Keep code blocks and examples readable with an 80-char line-length target.

## Code quality guardrails

- Keep code clean and tight: small focused functions, minimal branching,
  and clear naming.
- Avoid adding abstractions until duplication or complexity justifies them.
- Reuse shared CLI plumbing for cross-cutting behavior. Do not re-implement
  table rendering, JSON-to-table adaptation, output format switching, or
  common flag parsing per command.
- For table output, route through the shared renderer in
  `cli/output` (currently `JsonOutputRenderer` + `TableRenderer`) so table
  layout and CSV behavior stay consistent across commands.
- For shared flags/options, prefer existing `OptionGroup` utilities (such as
  `OutputOptions`) and extend those centrally when needed; avoid ad hoc
  command-local variants of the same flags.
- If a command needs behavior that current shared utilities do not support,
  update the shared utility first and then consume it from the command to
  prevent wheel reinvention.
- Preserve existing CLI behavior and output formats unless explicitly
  changing UX.
- Use `kotlin.test` annotations and `kotest` assertions (for example
  `shouldBe`) in tests.
- Add or update tests for behavior changes, especially around parsing,
  validation, and output.

## CLI safety and UX

- Keep destructive operations explicit and non-surprising.
- Preserve non-interactive automation behavior (`--yes`, machine-readable
  output) when modifying commands.
- For new flags/options, ensure help text is precise and reflected in
  generated manual output.

## Final checks before commit

- Build passes for `:ktsearch-cli`.
- Relevant tests pass.
- Docs are updated when command surface or behavior changes.
- Public API/docs comments are present where needed and remain concise.
