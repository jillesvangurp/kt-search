#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "${SCRIPT_DIR}/.." && pwd)
ENV_FILE="${SCRIPT_DIR}/.env"
IMAGE_NAME=${IMAGE_NAME:-kt-search-alerts-demo}

docker build -f "${SCRIPT_DIR}/Dockerfile" -t "${IMAGE_NAME}" "${SCRIPT_DIR}"

docker run --rm --env-file "${ENV_FILE}" "${IMAGE_NAME}" "$@"
