#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"

if ! command -v docker >/dev/null 2>&1; then
    echo "docker is required but not installed." >&2
    exit 1
fi

dockerfile="${script_dir}/docker/linux-native-builder.Dockerfile"
image="${KTSEARCH_DOCKER_IMAGE:-ktsearch-cli/linux-native-builder:amd64}"
docker_platform="${KTSEARCH_DOCKER_PLATFORM:-linux/amd64}"
gradle_cache_dir="${KTSEARCH_DOCKER_GRADLE_CACHE:-${repo_root}/.gradle-docker}"
output_dir="${script_dir}/build/docker-linux-binaries"

mkdir -p "${output_dir}"

echo "Building builder image ${image} for ${docker_platform}..."
docker build \
    --platform "${docker_platform}" \
    --file "${dockerfile}" \
    --tag "${image}" \
    "${repo_root}"

echo "Building Linux x86_64 + arm64 binaries in ${docker_platform} container..."
docker run --rm \
    --platform "${docker_platform}" \
    --volume "${repo_root}:/workspace/kt-search" \
    --volume "${gradle_cache_dir}:/gradle-home" \
    --workdir "/workspace/kt-search" \
    "${image}" \
    bash -lc "./gradlew --no-daemon --max-workers=1 \
-Dorg.gradle.jvmargs='-XX:MaxMetaspaceSize=512m -Xmx1g \
-Dkotlin.daemon.jvm.options=-Xmx1g' \
:ktsearch-cli:linkReleaseExecutableLinuxX64 \
:ktsearch-cli:linkReleaseExecutableLinuxArm64 \
-Pktsearch.linuxOnlyNativeTargets=true"

artifact_x64="${repo_root}/ktsearch-cli/build/bin/linuxX64/releaseExecutable/ktsearch.kexe"
artifact_arm64="${repo_root}/ktsearch-cli/build/bin/linuxArm64/releaseExecutable/ktsearch.kexe"

if [[ ! -f "${artifact_x64}" ]]; then
    echo "Missing artifact: ${artifact_x64}" >&2
    exit 1
fi
if [[ ! -f "${artifact_arm64}" ]]; then
    echo "Missing artifact: ${artifact_arm64}" >&2
    exit 1
fi

cp "${artifact_x64}" "${output_dir}/ktsearch-linux-x86_64"
cp "${artifact_arm64}" "${output_dir}/ktsearch-linux-arm64"
chmod +x "${output_dir}/ktsearch-linux-x86_64"
chmod +x "${output_dir}/ktsearch-linux-arm64"

echo "Linux binaries are ready in ${output_dir}:"
ls -lh "${output_dir}"
