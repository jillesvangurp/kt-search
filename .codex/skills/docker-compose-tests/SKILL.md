---
name: docker-compose-tests
description: Use Gradle Compose tasks to prepare and recover local Elasticsearch/OpenSearch test infrastructure in kt-search. Trigger this skill when tests or docs generation require a running cluster (especially localhost:9990), when instructions say to run ./gradlew composeUp before tests, or when compose startup fails due stale/conflicting containers.
---

# Docker Compose Tests

Use Gradle Compose tasks, not raw docker compose commands, for this repository.

## Standard Workflow

1. Start test infrastructure:
   `./gradlew composeUp`
2. Run the target tests (examples):
   `./gradlew :docs:test --tests '*DocumentationTest*' --rerun-tasks`
   `./gradlew :search-client:jvmTest`
3. Optionally stop infrastructure:
   `./gradlew composeDown`

## Recovery Workflow

If `composeUp` fails with container name conflicts (for example `/testes`):

1. `./gradlew composeDown`
2. `./gradlew composeUp`
3. Re-run the failed test command

## Rules

- Prefer `./gradlew composeUp` as the canonical startup path.
- Do not disable docs examples to bypass failing docs tests.
- Regenerate docs with test commands (not manual edits of generated outputs).
