# ktsearch-cli

`ktsearch-cli` is a multiplatform command line tool for Elasticsearch and
OpenSearch operations based on `kt-search`.

## Commands

- `ktsearch status`
- `ktsearch cat <variant>`
- `ktsearch index dump <index>`

`ktsearch index dump` writes a gzipped NDJSON file (`<index>.ndjson.gz`) where
each line is the document `_source`.

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

To force a specific installation path, set `KTSEARCH_INSTALL_DIR`.
