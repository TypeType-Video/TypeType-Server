#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

event_name="${GITHUB_EVENT_NAME:-push}"
base_ref="${GITHUB_BASE_REF:-}"
before="${GITHUB_EVENT_BEFORE:-}"
zero_sha="0000000000000000000000000000000000000000"
sha="${GITHUB_SHA:-$(git rev-parse HEAD)}"
output_file="${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"

emit_tasks() {
  if [[ -z "$1" ]]; then
    return
  fi

  {
    echo "tasks<<EOF"
    printf '%s\n' "$1"
    echo "EOF"
  } >>"$output_file"
}

if [[ "$event_name" != "push" && "$event_name" != "pull_request" ]]; then
  emit_tasks "build"
  exit 0
fi

if [[ "$event_name" == "pull_request" ]]; then
  if [[ -z "$base_ref" ]]; then
    echo "Missing base ref for pull request" >&2
    exit 1
  fi

  git fetch --no-tags origin "$base_ref"
  base_commit="$(git rev-parse "origin/$base_ref")"
  mapfile -d '' -t changed_files < <(
    git diff --name-only -z "$base_commit...$sha"
  )
elif [[ -n "$before" && "$before" != "$zero_sha" ]] &&
     git cat-file -e "$before^{commit}" 2>/dev/null; then
  mapfile -d '' -t changed_files < <(
    git diff --name-only -z "$before...$sha"
  )
elif git rev-parse --verify HEAD^ >/dev/null 2>&1; then
  mapfile -d '' -t changed_files < <(
    git diff --name-only -z "HEAD^...$sha"
  )
else
  emit_tasks "build"
  exit 0
fi

is_shared_change() {
  case "$1" in
    .github/scripts/* | .github/workflows/ci.yml | .github/workflows/coverage.yml)
      return 0
      ;;
    .github/workflows/openapi.yml)
      return 0
      ;;
    build.gradle.kts | settings.gradle.kts | gradle.properties | gradlew | gradle/*)
      return 0
      ;;
    openapi.yaml | openapi/* | src/*)
      return 0
      ;;
    Dockerfile)
      return 0
      ;;
  esac

  return 1
}

is_ignored_change() {
  case "$1" in
    *.md | *.png | *.svg | .gitignore | .editorconfig | LICENSE*)
      return 0
      ;;
    docs/* | .github/ISSUE_TEMPLATE/*)
      return 0
      ;;
  esac

  return 1
}

full_build=false
declare -A changed_modules=()

for file in "${changed_files[@]}"; do
  if is_ignored_change "$file"; then
    continue
  fi

  if is_shared_change "$file"; then
    full_build=true
    continue
  fi

  if [[ "$file" == server-*/* ]]; then
    module="${file%%/*}"
    changed_modules["$module"]=1
  else
    full_build=true
  fi
done

if [[ "$full_build" == true ]]; then
  emit_tasks "build"
  exit 0
fi

if [[ ${#changed_modules[@]} -eq 0 ]]; then
  exit 0
fi

declare -A dependents_by_module=()
for build_file in server-*/build.gradle.kts; do
  module="${build_file%/*}"
  while IFS= read -r line; do
    if [[ "$line" =~ project\(\":([A-Za-z0-9_-]+)\"\) ]]; then
      dependency="${BASH_REMATCH[1]}"
      dependents_by_module["$dependency"]+="$module "
    fi
  done <"$build_file"
done

declare -A affected_modules=()

mark_affected() {
  local module="$1"

  if [[ -n "${affected_modules[$module]:-}" ]]; then
    return
  fi

  affected_modules["$module"]=1

  local dependent
  for dependent in ${dependents_by_module["$module"]:-}; do
    mark_affected "$dependent"
  done
}

for module in "${!changed_modules[@]}"; do
  if [[ ! -f "$module/build.gradle.kts" ]]; then
    echo "Changed module no longer exists: $module" >&2
    emit_tasks "build"
    exit 0
  fi

  mark_affected "$module"
done

module_tasks="$(
  for module in "${!affected_modules[@]}"; do
    printf ':%s:build\n' "$module"
  done | sort
)"

emit_tasks "$module_tasks"
