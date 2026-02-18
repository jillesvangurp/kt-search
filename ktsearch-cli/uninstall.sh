#!/usr/bin/env bash
set -euo pipefail

os="$(uname -s)"

if [[ -n "${KTSEARCH_INSTALL_DIR:-}" ]]; then
    candidate_dirs=("${KTSEARCH_INSTALL_DIR}")
else
    if [[ "${os}" == "Darwin" ]]; then
        candidate_dirs=(
            "/opt/homebrew/bin"
            "/usr/local/bin"
            "${HOME}/.local/bin"
            "${HOME}/bin"
        )
    else
        candidate_dirs=(
            "${HOME}/.local/bin"
            "/usr/local/bin"
            "${HOME}/bin"
        )
    fi
fi

removed=0
for dir in "${candidate_dirs[@]}"; do
    path="${dir}/ktsearch"
    if [[ -f "${path}" ]]; then
        rm -f "${path}"
        echo "Removed ${path}"
        removed=1
    fi
done

if [[ "${removed}" -eq 0 ]]; then
    echo "No installed ktsearch binary found in expected locations."
fi
