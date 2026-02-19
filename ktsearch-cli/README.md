# ktsearch-cli

`ktsearch-cli` is a multiplatform command line tool for
Elasticsearch and OpenSearch operations based on `kt-search`.
It supports Elasticsearch `7-9` and OpenSearch `1-3`, with
installable binaries for macOS/Linux and Bash/Zsh completion.

The full command reference is generated in
[`cli-manual.md`](./cli-manual.md).

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

