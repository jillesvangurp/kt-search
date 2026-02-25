# ktsearch-cli

`ktsearch-cli` is a multiplatform command line tool for
Elasticsearch and OpenSearch operations based on `kt-search`.
It supports Elasticsearch `7-9` and OpenSearch `1-3`, with
installable native binaries for macOS/Linux and Bash/Zsh
completion.

The full command reference is generated in
[`cli-manual.md`](./cli-manual.md).

## Why native binaries

A key advantage of `kt-search` being multiplatform is that
`ktsearch-cli` ships as native binaries on macOS and Linux.

- Easy installation
- No runtime dependencies
- Fast startup
- Easy to script
- Easy to use for agentic coding workflows

## How this works

The CLI is a thin layer on top of `kt-search`.
It uses [Clikt](https://ajalt.github.io/clikt/) for
command-line parsing and [Okio](https://square.github.io/okio/)
for native I/O.

## Quick command map

Use this as a high-level map. For full command syntax,
flags, and more examples, see
[`cli-manual.md`](./cli-manual.md).

- `ktsearch cluster ...` for health, stats, state, and settings.
- `ktsearch cat ...` for table/csv operational views.
- `ktsearch index create|get|delete|search ...` for index-level operations.
- `ktsearch index mappings/settings/template ...` for schema and template management.
- `ktsearch index alias ...` and `ktsearch index data-stream ...` for routing/data stream tasks.
- `ktsearch index snapshot ...`, `reindex ...`, and `ilm ...` for maintenance workflows.
- `ktsearch index dump|restore ...` for NDJSON export/import.

Commands that modify or delete data ask for confirmation
by default. Use `--yes` in automation.

## Native build limitations

Native support in Kotlin/Native and dependency toolchains
keeps improving, and some limits below may change in
future releases.

- Main focus is reliable support for `macosArm64`, `macosX64`, and `linuxArm64`.
- `linuxX64` is supported and can be cross-built on macOS with `-Pktsearch.enableLinuxTargetsOnMac=true`.
- Direct host cross-builds for `linuxArm64` on macOS may still fail at link time due to `ktor-client-curl`/OpenSSL linker issues.
- Use `./ktsearch-cli/build-linux-binaries-docker.sh` to build Linux `x86_64` and `arm64` binaries via Docker on macOS and Linux.
- Building Apple final binaries on Linux CI is not supported by Kotlin/Native host restrictions.
- Windows `mingwX64` binaries can be built, but Windows ARM64 is not available as a Kotlin/Native target today.
- For now, the easiest way to get a native binary for your system is to run the build on that same system via `./ktsearch-cli/install.sh`.
- Prebuilt packaging and CI-produced binaries may be added later if this stabilizes.

## Linux Docker builds

Build Linux native binaries for both `x86_64` and
`arm64` with Docker:

```bash
println("./ktsearch-cli/build-linux-binaries-docker.sh")
```

Captured Output:

```
./ktsearch-cli/build-linux-binaries-docker.sh

```

The script creates per-architecture builder images with
Java and native toolchain dependencies, runs Gradle in a
Linux `amd64` container, cross-compiles both targets, and
writes output binaries to:
`ktsearch-cli/build/docker-linux-binaries/`

- Override container platform with `KTSEARCH_DOCKER_PLATFORM` (default `linux/amd64`).
- Override image name with `KTSEARCH_DOCKER_IMAGE`.
- Override Gradle cache location with `KTSEARCH_DOCKER_GRADLE_CACHE`.

## Wasm builds (experimental)

`ktsearch-cli` has experimental WebAssembly support.

- `wasmJs` status: supported as an opt-in target (`-Pktsearch.enableWasmCli=true`) and runnable with Node.js.
- Build optimized wasm executable artifacts:
- `./gradlew :ktsearch-cli:compileProductionExecutableKotlinWasmJs -Pktsearch.enableWasmCli=true`
- Run from Node.js:
- `./gradlew :ktsearch-cli:wasmJsNodeProductionRun -Pktsearch.enableWasmCli=true`
- `wasmJs` limitation: platform I/O is partial. Commands that require local file read/write or gzip dump/restore are not supported yet.
- `wasmWasi` status: not supported yet in this project.
- `wasmWasi` blocker: WASI runtimes (for example `wasmtime`) are not wired yet. That would require adding a `wasmWasi` target plus platform I/O implementations.

## Install / uninstall

Use the scripts from the repository root:

```bash
println("./ktsearch-cli/install.sh")
println("./ktsearch-cli/uninstall.sh")
```

Captured Output:

```
./ktsearch-cli/install.sh
./ktsearch-cli/uninstall.sh

```

`install.sh` builds and installs `ktsearch` for the current
macOS/Linux host and also installs Bash/Zsh completion.

For installation behavior details (install path selection
and completion path overrides), see
[`cli-manual.md`](./cli-manual.md).

## Examples

For more examples and all flags, see
[`cli-manual.md`](./cli-manual.md).

```bash
println("ktsearch cluster health")
println("ktsearch info")
println("ktsearch cat indices")
println("ktsearch index create products")
println("ktsearch index wait-green products")
```

Captured Output:

```
ktsearch cluster health
ktsearch info
ktsearch cat indices
ktsearch index create products
ktsearch index wait-green products

```

## Environment

Configure connection defaults via environment variables:

- `KTSEARCH_HOST`
- `KTSEARCH_PORT`
- `KTSEARCH_HTTPS`
- `KTSEARCH_USER`
- `KTSEARCH_PASSWORD`
- `KTSEARCH_ELASTIC_API_KEY`
- `KTSEARCH_LOGGING`

## Completion

Generate completion scripts for your shell:

```bash
println("ktsearch completion bash")
println("ktsearch completion zsh")
println("ktsearch completion fish")
```

Captured Output:

```
ktsearch completion bash
ktsearch completion zsh
ktsearch completion fish

```

## Build artifacts

- Native executable: `ktsearch`
- JVM fat jar: `./gradlew :ktsearch-cli:jvmFatJar`

## Scriptability and agentic workflows

`ktsearch-cli` is intended for day-to-day operational
scripts.

- Runs well in non-interactive mode with explicit flags like `--yes`.
- Emits machine-friendly output (`--csv` for cat commands, JSON for API-style commands).
- JSON output works well with tools like `jq` for filtering and extraction in shell scripts.
- Uses environment variables for repeatable CI/dev shell setup.

It also works well with agentic coding tools like Codex
and Claude Code. Because commands are explicit and
composable, agents can safely perform common index and
cluster operations in runbooks and automation tasks.

> Minimal skill snippet you can add to your project
> `AGENTS.md`:
>
> ```md
> ## Skill: ktsearch-ops
> Use `ktsearch` as the default CLI for
> Elasticsearch/OpenSearch operations.
>
> - Prefer `ktsearch` over raw `curl` when a matching
>   command exists.
> - Use non-interactive flags in automation (`--yes`
>   where needed).
> - For tabular cat output in scripts, use `--csv`.
> - For reproducibility, prefer env vars:
>   `KTSEARCH_HOST`, `KTSEARCH_PORT`, `KTSEARCH_HTTPS`,
>   `KTSEARCH_USER`, `KTSEARCH_PASSWORD`.
> - Always print the exact command before executing
>   destructive actions.
> ```

## Related tools

The tools below are useful alternatives or complements.

 Tool | What it is good at | Compared to `ktsearch-cli` |
---|---|---|
 `ecctl` (Elastic Cloud) | Managing Elastic Cloud deployments, traffic filters, and platform settings. | Cloud-control focused. `ktsearch-cli` focuses on index/cluster APIs. |
 `opensearch-cli` / AWS CLI (OpenSearch) | OpenSearch plugin workflows and Amazon OpenSearch domain/service operations. | Useful for service/domain provisioning and plugin commands. `ktsearch-cli` focuses on search/index operations. |
 `curl` + `jq` | Universal access to any endpoint. | Very flexible, but no domain-specific commands, no built-in safety prompts, and no integrated completion model. |
 `elasticdump` | Data migration/export workflows. | Strong ETL focus, but not a general-purpose operational CLI for aliases/templates/snapshots/ILM in one tool. |
 `elasticsearch-curator` | Policy-style index housekeeping jobs. | Great for scheduled maintenance; less suited as an interactive daily CLI for both Elasticsearch and OpenSearch generations. |
 OpenSearch/Elastic Dev Tools consoles | Interactive request authoring in UI. | Excellent for ad hoc requests, but browser-based and not ideal for shell automation in CI/scripts. |

