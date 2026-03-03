#!/usr/bin/env bash
# Usage: source ./ktsearch-temp.sh
# (must be sourced, not executed, so alias/completion stay in current shell)

# After you upload the jar file and install java on your remote machine, source this to get the cli
KTSEARCH_JAR="/root/ktsearch-cli-all.jar"

if [[ ! -f "$KTSEARCH_JAR" ]]; then
  echo "Jar not found: $KTSEARCH_JAR" >&2
  return 1 2>/dev/null || exit 1
fi

# Temporary alias for this shell session
alias ktsearch="java -jar $KTSEARCH_JAR"

# Detect current shell and install completion in-memory
if [[ -n "${BASH_VERSION:-}" ]]; then
  eval "$("java" -jar "$KTSEARCH_JAR" completion bash)"
  echo "ktsearch alias + bash completion loaded for this session."
elif [[ -n "${ZSH_VERSION:-}" ]]; then
  autoload -Uz compinit
  compinit -u
  eval "$("java" -jar "$KTSEARCH_JAR" completion zsh)"
  echo "ktsearch alias + zsh completion loaded for this session."
else
  echo "Unsupported shell for auto-completion. Alias loaded only."
fi