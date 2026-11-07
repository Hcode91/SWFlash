#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
package_spec="${1:-@ruffle-rs/ruffle}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
cd "$work_dir"
npm pack "$package_spec" --pack-destination "$work_dir" > package-name.txt
archive="$(tail -n 1 package-name.txt)"
tar -xzf "$archive"
test -f package/ruffle.js
mkdir -p "$project_dir/app/src/main/assets/player/ruffle"
cp -R package/. "$project_dir/app/src/main/assets/player/ruffle/"
node -e 'const p=require("./package/package.json"); console.log(p.name+"@"+p.version)' > "$project_dir/RUFFLE_VERSION.txt"
printf 'Bundled %s\n' "$(cat "$project_dir/RUFFLE_VERSION.txt")"
