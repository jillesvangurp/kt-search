---
name: kt-search-release
description: Use this skill when the user wants to cut, publish, tag, or create a GitHub release for kt-search, especially when the task includes version bumping, validating that commits are pushed, checking GitHub Actions stability, preparing release notes from diffs, or attaching CLI binaries for Homebrew-style installation.
---

# KT Search Release

Use this skill for kt-search release work. It is repo-specific.

## What this skill does

- Verifies the repo is safe to release from.
- Forces an explicit version confirmation from the user before any publish
  or tag step.
- Checks that the release commit is already pushed and that GitHub Actions
  runs for that commit on `master` are complete and successful.
- Publishes with the repo's `publish.sh`.
- Builds and packages the CLI native binaries for GitHub release assets.
- Drafts release notes from the diff since the previous tag and creates the
  GitHub release with attached assets.

## Trigger phrases

Use this skill when the user asks to:

- cut a release
- bump the version and publish
- tag a new release
- create a GitHub release
- attach ktsearch CLI binaries
- prepare a release for Homebrew support

## Workflow

1. Identify the latest semantic version tag with:
   `git tag --sort=-version:refname | sed -n '1,20p'`
2. Propose the next version, but stop and confirm the exact version with the
   user before any publish, tag, or release action.
3. Run the readiness gate:
   `./.codex/skills/kt-search-release/scripts/release_support.sh check <version>`
4. If the readiness check confirms `HEAD == origin/master` and GitHub Actions
   is complete and green, do not re-run local Gradle verification.
5. Run exactly one Gradle command for local build/publish work:
   `./gradlew -Pversion="<version>" :ktsearch-cli:assemble publish`
   If the readiness check already confirms `HEAD == origin/master` and GitHub
   Actions is green, this command can still be used as the single publish step
   without any extra local test reruns.
6. Create the git tag:
   `git tag "<version>"`
7. Push the tag:
   `git push --tags`
8. Always package and attach the CLI binaries:
   `./.codex/skills/kt-search-release/scripts/release_support.sh package <version>`
9. Summarize changes from the previous tag:
   `git log --oneline --no-merges <previous>..HEAD`
   `git diff --stat <previous>..HEAD`
10. Check for merged PRs in the range and preserve author attribution:
   `git log --merges --oneline <previous>..HEAD`
   `gh pr view <number> --json author,title,url`
11. Draft the release notes from the diff and merged PRs, then show that
    draft to the user and confirm it before creating the GitHub release.
12. Create the GitHub release with:
    `gh release create <version> <asset...> --title <version> --notes-file <file>`
13. Verify the uploaded assets and release URL with:
    `gh release view <version> --json url,assets,name,tagName,publishedAt`

## Guardrails

- Never release from a dirty worktree.
- Never release if `HEAD` is not exactly at `origin/master`.
- Never release if the target tag already exists locally or remotely.
- Never release if a GitHub release for the version already exists.
- Never release if GitHub Actions for the release commit are still running,
  failed, or were not found.
- Never infer the target version when a publish/tag action is about to happen.
  Ask the user to confirm it.
- Always confirm the drafted release notes with the user before creating the
  GitHub release.
- Use one explicit Gradle invocation for release work:
  `./gradlew -Pversion="<version>" :ktsearch-cli:assemble publish`
- Do not add extra local test reruns when the release commit is already pushed
  and GitHub Actions is green.
- Use the diff from the previous tag to write release notes. Do not invent
  changes that are not present in git history.
- Always attach the packaged native CLI tarballs and generated checksums file.
- Give credit in the release notes when the range contains merged PRs from
  external contributors.

## Release notes guidance

- Start with a one-line summary of the release theme.
- Group the diff into 3-6 concrete bullets.
- Prefer user-visible changes, release/build changes, and packaging changes.
- If merged PRs in the range were authored by someone other than
  `@jillesvangurp`, add a short thanks line or preserve `by @author` credit in
  the bullets.
- End with the compare link:
  `https://github.com/jillesvangurp/kt-search/compare/<previous>...<version>`

## Assets

The packaging script produces:

- `ktsearch-<version>-darwin-arm64.tar.gz`
- `ktsearch-<version>-darwin-x64.tar.gz`
- `ktsearch-<version>-linux-x64.tar.gz`
- `checksums.txt`

Each tarball contains:

- `ktsearch`
- `install.sh`
- `uninstall.sh`
- `alias-and-completions.sh`

## Script

- Consolidated release helper:
  `scripts/release_support.sh`
