#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"

os="$(uname -s)"
arch="$(uname -m)"

target=""
target_dir=""
case "${os}" in
    Darwin)
        case "${arch}" in
            arm64|aarch64)
                target="MacosArm64"
                target_dir="macosArm64"
                ;;
            x86_64)
                target="MacosX64"
                target_dir="macosX64"
                ;;
            *)
                echo "Unsupported macOS architecture: ${arch}" >&2
                exit 1
                ;;
        esac
        ;;
    Linux)
        case "${arch}" in
            x86_64)
                target="LinuxX64"
                target_dir="linuxX64"
                ;;
            arm64|aarch64)
                target="LinuxArm64"
                target_dir="linuxArm64"
                ;;
            *)
                echo "Unsupported Linux architecture: ${arch}" >&2
                exit 1
                ;;
        esac
        ;;
    *)
        echo "Unsupported OS: ${os}. Only macOS and Linux are supported." >&2
        exit 1
        ;;
esac

detect_bash_completion_dir() {
    if [[ -n "${KTSEARCH_BASH_COMPLETION_DIR:-}" ]]; then
        echo "${KTSEARCH_BASH_COMPLETION_DIR}"
        return
    fi

    local xdg_dir="${XDG_DATA_HOME:-${HOME}/.local/share}"
    local user_completion_dir="${xdg_dir}/bash-completion/completions"
    local -a candidate_completion_dirs=()

    if [[ "${os}" == "Darwin" ]]; then
        candidate_completion_dirs=(
            "/opt/homebrew/share/bash-completion/completions"
            "/usr/local/share/bash-completion/completions"
            "${user_completion_dir}"
        )
    else
        candidate_completion_dirs=(
            "${user_completion_dir}"
            "/usr/local/share/bash-completion/completions"
            "/usr/share/bash-completion/completions"
        )
    fi

    local completion_dir=""
    local dir
    for dir in "${candidate_completion_dirs[@]}"; do
        if [[ -d "${dir}" && -w "${dir}" ]]; then
            completion_dir="${dir}"
            break
        fi
    done

    if [[ -z "${completion_dir}" ]]; then
        completion_dir="${user_completion_dir}"
    fi

    echo "${completion_dir}"
}

detect_zsh_completion_dir() {
    if [[ -n "${KTSEARCH_ZSH_COMPLETION_DIR:-}" ]]; then
        echo "${KTSEARCH_ZSH_COMPLETION_DIR}"
        return
    fi

    local xdg_dir="${XDG_DATA_HOME:-${HOME}/.local/share}"
    local user_completion_dir="${xdg_dir}/zsh/site-functions"
    local -a candidate_completion_dirs=()

    if [[ "${os}" == "Darwin" ]]; then
        candidate_completion_dirs=(
            "/opt/homebrew/share/zsh/site-functions"
            "/usr/local/share/zsh/site-functions"
            "${user_completion_dir}"
        )
    else
        candidate_completion_dirs=(
            "${user_completion_dir}"
            "/usr/local/share/zsh/site-functions"
            "/usr/share/zsh/site-functions"
        )
    fi

    local completion_dir=""
    local dir
    for dir in "${candidate_completion_dirs[@]}"; do
        if [[ -d "${dir}" && -w "${dir}" ]]; then
            completion_dir="${dir}"
            break
        fi
    done

    if [[ -z "${completion_dir}" ]]; then
        completion_dir="${user_completion_dir}"
    fi

    echo "${completion_dir}"
}

if [[ -n "${KTSEARCH_INSTALL_DIR:-}" ]]; then
    install_dir="${KTSEARCH_INSTALL_DIR}"
else
    candidate_dirs=()
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

    install_dir=""
    for dir in "${candidate_dirs[@]}"; do
        if [[ -d "${dir}" && -w "${dir}" ]]; then
            install_dir="${dir}"
            break
        fi
    done

    if [[ -z "${install_dir}" ]]; then
        install_dir="${HOME}/.local/bin"
    fi
fi

mkdir -p "${install_dir}"

echo "Building native executable for ${os} ${arch}..."
"${repo_root}/gradlew" ":ktsearch-cli:linkReleaseExecutable${target}"

binary_path="${repo_root}/ktsearch-cli/build/bin/${target_dir}/releaseExecutable/ktsearch.kexe"
if [[ ! -f "${binary_path}" ]]; then
    binary_path="$(find "${repo_root}/ktsearch-cli/build/bin/${target_dir}" \
        -type f \
        -path "*/releaseExecutable/ktsearch.kexe" \
        | head -n 1)"
fi

if [[ -z "${binary_path}" || ! -f "${binary_path}" ]]; then
    echo "Could not locate built executable." >&2
    exit 1
fi

cp "${binary_path}" "${install_dir}/ktsearch"
chmod +x "${install_dir}/ktsearch"

bash_completion_dir="$(detect_bash_completion_dir)"
mkdir -p "${bash_completion_dir}"
"${install_dir}/ktsearch" completion bash > "${bash_completion_dir}/ktsearch"

zsh_completion_dir="$(detect_zsh_completion_dir)"
mkdir -p "${zsh_completion_dir}"
"${install_dir}/ktsearch" completion zsh > "${zsh_completion_dir}/_ktsearch"

echo "Installed ktsearch to ${install_dir}/ktsearch"
echo "Installed bash completion to ${bash_completion_dir}/ktsearch"
echo "Installed zsh completion to ${zsh_completion_dir}/_ktsearch"
if [[ ":${PATH}:" != *":${install_dir}:"* ]]; then
    echo "Note: ${install_dir} is not on PATH."
fi
