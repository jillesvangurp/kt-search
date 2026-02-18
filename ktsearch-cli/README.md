# ktsearch-cli

`ktsearch-cli` is a multiplatform command line tool for Elasticsearch and
OpenSearch operations based on `kt-search`.

## Commands

- `ktsearch status`
- `ktsearch cat <variant>`
- `ktsearch index dump <index>`
- `ktsearch index restore <index>`
- `ktsearch index create|get|show|exists|delete|rm <index>`
- `ktsearch index mappings get|put <index>`
- `ktsearch index settings get|put <index>`
- `ktsearch index template component get|put|delete [template-id]`
- `ktsearch index template index get|put|delete [template-id]`
- `ktsearch index data-stream create|get|delete [name]`
- `ktsearch index alias get|update|add|remove|remove-index ...`
- `ktsearch index doc get|index|delete|mget ...`
- `ktsearch index snapshot repo create|get|delete|verify ...`
- `ktsearch index snapshot create|list|delete|restore ...`
- `ktsearch index reindex|reindex-task-status|reindex-wait ...`
- `ktsearch index ilm put|get|delete|status ...`
- `ktsearch index apply <target> --file ... [--kind auto|...]`
- `ktsearch index wait-green <index>`
- `ktsearch index wait-exists <index>`
- `ktsearch index search <index> --query ...`

`ktsearch index dump` writes a gzipped NDJSON file (`<index>.ndjson.gz`) where
each line is the document `_source`.

`ktsearch index restore` loads gzipped NDJSON into an index with bulk indexing.
Use `--recreate` to drop and recreate the target index first.
You can also control `--pipeline`, `--routing`, `--refresh`, and `--id-field`.

Destructive commands prompt for confirmation by default. Use `--yes` to skip
prompts. Some commands support `--dry-run` for request preview.

`ktsearch cat <variant>` supports:
`aliases`, `allocation`, `count`, `health`, `indices`, `master`, `nodes`,
`pending-tasks`, `recovery`, `repositories`, `shards`, `snapshots`, `tasks`,
`templates`, and `thread-pool`.

By default cat output is rendered as an aligned table. Use `--csv` to render
CSV output.

## Examples

```bash
ktsearch status
ktsearch cat health
ktsearch cat indices products-* --columns health,status,index,docs.count
ktsearch cat nodes --csv
ktsearch --host localhost --port 9200 index dump products
ktsearch index restore products --input products.ndjson.gz
ktsearch index create products
ktsearch index mappings put products -f mappings.json
ktsearch index settings put products -d '{"index":{"number_of_replicas":1}}'
ktsearch index template component put logs-settings -f settings-template.json
ktsearch index template index put logs-template -f index-template.json
ktsearch index data-stream create logs-prod
ktsearch index alias add products-v2 products --write true
ktsearch index alias remove-index products-v1 --yes
ktsearch index doc get products 42
ktsearch index doc index products --id 42 -d '{"name":"updated"}'
ktsearch index snapshot repo create fs-repo -f repo.json
ktsearch index snapshot create fs-repo nightly -d '{"indices":"products-*"}'
ktsearch index reindex --wait false -d '{"source":{"index":"a"},"dest":{"index":"b"}}'
ktsearch index reindex-task-status r1A2WoRbTwKZ516z6NEs5A:36619
ktsearch index ilm get my-policy
ktsearch index apply products -f mappings.json --kind mappings
ktsearch index wait-green products
ktsearch --https --user elastic --password secret index dump products --yes
```

## Environment variables

- `KTSEARCH_HOST`
- `KTSEARCH_PORT`
- `KTSEARCH_HTTPS`
- `KTSEARCH_USER`
- `KTSEARCH_PASSWORD`
- `KTSEARCH_LOGGING`

## Completion

Generate completion scripts via the built-in command:

```bash
ktsearch completion bash
ktsearch completion zsh
ktsearch completion fish
```

## Build artifacts

- Native executable: `ktsearch` (host-supported native targets)
- JVM fat jar: `./gradlew :ktsearch-cli:jvmFatJar`

## Install / uninstall

Use the provided scripts from the repository root:

```bash
./ktsearch-cli/install.sh
./ktsearch-cli/uninstall.sh
```

`install.sh` builds the native release executable for your current macOS/Linux
host (incremental build) and installs `ktsearch` in a writable bin directory:

- macOS: `/opt/homebrew/bin`, `/usr/local/bin`, `~/.local/bin`, or `~/bin`
- Linux: `~/.local/bin`, `/usr/local/bin`, or `~/bin`

It also installs shell completions in writable completion dirs:

- Bash as `ktsearch` in bash-completion directories
- Zsh as `_ktsearch` in zsh `site-functions` directories

To force specific paths, set `KTSEARCH_INSTALL_DIR` and/or
`KTSEARCH_BASH_COMPLETION_DIR` and/or `KTSEARCH_ZSH_COMPLETION_DIR`.
