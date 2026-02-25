#!/usr/bin/env bash
set -euo pipefail

os="$(uname -s)"

detect_bash_completion_dirs() {
    if [[ -n "${KTSEARCH_BASH_COMPLETION_DIR:-}" ]]; then
        echo "${KTSEARCH_BASH_COMPLETION_DIR}"
        return
    fi

    local xdg_dir="${XDG_DATA_HOME:-${HOME}/.local/share}"
    local user_completion_dir="${xdg_dir}/bash-completion/completions"

    if [[ "${os}" == "Darwin" ]]; then
        echo "/opt/homebrew/share/bash-completion/completions"
        echo "/usr/local/share/bash-completion/completions"
        echo "${user_completion_dir}"
    else
        echo "${user_completion_dir}"
        echo "/usr/local/share/bash-completion/completions"
        echo "/usr/share/bash-completion/completions"
    fi
}

detect_zsh_completion_dirs() {
    if [[ -n "${KTSEARCH_ZSH_COMPLETION_DIR:-}" ]]; then
        echo "${KTSEARCH_ZSH_COMPLETION_DIR}"
        return
    fi

    local xdg_dir="${XDG_DATA_HOME:-${HOME}/.local/share}"
    local user_completion_dir="${xdg_dir}/zsh/site-functions"

    if [[ "${os}" == "Darwin" ]]; then
        echo "/opt/homebrew/share/zsh/site-functions"
        echo "/usr/local/share/zsh/site-functions"
        echo "${user_completion_dir}"
    else
        echo "${user_completion_dir}"
        echo "/usr/local/share/zsh/site-functions"
        echo "/usr/share/zsh/site-functions"
    fi
}

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

completion_removed=0
while IFS= read -r completion_dir; do
    completion_path="${completion_dir}/ktsearch"
    if [[ -f "${completion_path}" ]]; then
        rm -f "${completion_path}"
        echo "Removed ${completion_path}"
        completion_removed=1
    fi
done < <(detect_bash_completion_dirs)

if [[ "${completion_removed}" -eq 0 ]]; then
    echo "No bash completion file found in expected locations."
fi

zsh_completion_removed=0
while IFS= read -r completion_dir; do
    completion_path="${completion_dir}/_ktsearch"
    if [[ -f "${completion_path}" ]]; then
        rm -f "${completion_path}"
        echo "Removed ${completion_path}"
        zsh_completion_removed=1
    fi
done < <(detect_zsh_completion_dirs)

if [[ "${zsh_completion_removed}" -eq 0 ]]; then
    echo "No zsh completion file found in expected locations."
fi
