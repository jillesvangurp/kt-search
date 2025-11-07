# Project Overview

This repository contains the **KT Search** Kotlin multiplatform client. It offers DSLs and utilities for working with Elasticsearch and OpenSearch clusters from Kotlin code. The build is managed with Gradle and the project is organized into several modules and supporting assets.

## Repository Structure

- `search-dsls/` – Kotlin Multiplatform module that provides the core JSON and query DSLs. Targets JVM, JS, native (Linux, macOS, Windows, iOS), and WASM, and enables opt-in APIs for expect/actual classes and experimental Kotlin features.【F:settings.gradle.kts†L14-L18】【F:search-dsls/build.gradle.kts†L1-L84】
- `search-client/` – Kotlin Multiplatform module that builds on the DSLs to implement the actual HTTP client integrations, multiplatform targets, and publication setup. Includes Docker Compose tooling for integration tests against different Elasticsearch/OpenSearch versions.【F:settings.gradle.kts†L14-L18】【F:search-client/build.gradle.kts†L1-L120】
- `docs/` – Auxiliary JVM module whose tests generate the manual, README, and Quarto inputs from Kotlin scripts. Outputs land in `docs/build/manual` and `docs/build/manual-quarto`.【F:settings.gradle.kts†L14-L18】【F:docs/src/test/kotlin/documentation/DocumentationTest.kt†L1-L101】
- `docs/build/manual-quarto/` – Contains Quarto-ready markdown and configuration; rendered to HTML, PDF, or EPUB with the provided `quarto.sh` script.【F:docs/src/test/kotlin/documentation/DocumentationTest.kt†L60-L101】【F:quarto.sh†L1-L4】
- Root Gradle files (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `versions.properties`) configure shared repositories, Dokka publication, and dependency versions.【F:build.gradle.kts†L1-L96】【F:settings.gradle.kts†L1-L18】
- Additional tooling directories like `es_kibana/`, `jupyter-example/`, and `kotlin-js-store/` provide integration-test stacks, notebook samples, and JS artifacts respectively.【F:README.md†L55-L116】

## Building the Project

The project uses the Gradle Wrapper. Typical workflows:

1. **Yarn lock issies**. If there are issues with the yarn lock file, simply regenerate it with `./gradlew kotlinUpgradeYarnLock`
1. **Start elasticsearch** before tests (required by docs and search-client tests). `./gradlew composeUp` 
1. **Partial tests** `./gradlew jvmTest` Runs just the Java tests; rely on full CI build for multiplatform tests and matrix tests against different elasticsearch and opensearch versions.

## Documentation Generation

Documentation lives under the `docs` module and is produced by running the module’s tests:

1. Execute `./gradlew :docs:test --rerun-tasks`. The `DocumentationTest` class materializes markdown for the manual, README, and Quarto book structure under `docs/build/manual` and `docs/build/manual-quarto`. It also creates `_quarto.yml` with the chapter listing【F:docs/src/test/kotlin/documentation/DocumentationTest.kt†L25-L101). Adding the rerun-tasks flag ensures documentation is regenerated.
2. To publish rich formats, run the `quarto.sh` helper script from the repository root after the tests complete. It mounts the generated files into the official Quarto Docker image and renders HTML (and optionally PDF/EPUB) outputs inside `docs/build/manual-quarto/quarto`.【F:quarto.sh†L1-L4】【F:docs/src/test/kotlin/documentation/DocumentationTest.kt†L83-L101】
3. Note. disabling examples in the docs module is not an acceptable change in case the test run fails. Assume the problem is with the branch and the modifications or with the way the tests are run (is elasticsearch running?).

## Pull requests

- ensure code compiles for all platforms
- ensure tests have been added
- ensure relevant documentation is updated (if needed)

These steps keep the manual synchronized with the source snippets and ensure the GitHub Pages site stays up to date.【F:README.md†L25-L46】
