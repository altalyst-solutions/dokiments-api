#!/usr/bin/env bash
#
# Runs IntelliJ IDEA's code inspections on the whole project from the command line and lists
# the problems by file, the same ones the editor shows. Use it before opening a pull request,
# for what the build itself can't check: Spring configuration, YAML, Gradle scripts, docs.
#
#   scripts/inspect.sh
#
# Needs IntelliJ IDEA installed. It runs with its own temporary settings, so it works while
# the IDE is open and doesn't change your IDE settings. Set IDEA_HOME if IntelliJ isn't in
# /Applications/IntelliJ IDEA.app.
#
# Some findings are known not to apply here and are listed separately at the end instead of
# with the problems:
# - Grazie spelling and style: the project is written in British English, which Grazie flags
#   by default ("colour", "behaviour").
# - Inputs of GitHub Actions: this command-line run can't download each action's description,
#   so every input looks undefined. The editor resolves them.
# - Anything under build/, which is generated.
# - A few specific findings that are intentional (see ACCEPTED below for each reason).

set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
idea_home="${IDEA_HOME:-/Applications/IntelliJ IDEA.app/Contents}"
inspect="${idea_home}/bin/inspect.sh"

if [[ ! -x "$inspect" ]]; then
  echo "IntelliJ IDEA not found at ${idea_home}. Set IDEA_HOME to its Contents folder (macOS) or install folder." >&2
  exit 1
fi

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir:?}"' EXIT
mkdir -p "$work_dir"/{config,system,log,out}

# Separate settings and caches, so this isn't blocked by (and doesn't touch) a running IDE.
cat > "$work_dir/idea.properties" <<EOF
idea.config.path=${work_dir}/config
idea.system.path=${work_dir}/system
idea.log.path=${work_dir}/log
EOF

# An empty profile means IntelliJ's default inspections, as in a fresh editor.
cat > "$work_dir/profile.xml" <<'EOF'
<profile version="1.0">
  <option name="myName" value="Default" />
</profile>
EOF

echo "Running IntelliJ inspections (about a minute)..."
if ! IDEA_PROPERTIES="$work_dir/idea.properties" "$inspect" "$project_dir" "$work_dir/profile.xml" \
  "$work_dir/out" -v0 -format json > "$work_dir/run.log" 2>&1; then
  echo "IntelliJ couldn't inspect the project:" >&2
  tail -n 20 "$work_dir/run.log" >&2
  exit 1
fi

python3 - "$work_dir/out" <<'EOF'
import json, os, re, sys
from collections import Counter

out = sys.argv[1]
known = {"GrazieInspection", "GrazieStyle", "UndefinedParamsPresent"}

# Findings that are intentional, by file and inspection. Keep this list short and give each a
# reason: everything else should be fixed.
ACCEPTED = {
    # JAVA_HOME is set by the base image, and PORT by ENV but used inside the health check's
    # command string, so the IDE can't see either.
    ("Dockerfile", "Annotator"),
    # align="center" is the only way to centre content in a GitHub README, which strips CSS.
    ("README.md", "HtmlDeprecatedAttribute"),
    # The logo is a 512 px image shown at 96 px on purpose, so it stays sharp on dense screens.
    ("README.md", "CheckImageSize"),
}
problems, skipped = [], Counter()

for name in sorted(os.listdir(out)):
    if not name.endswith(".json"):
        continue
    inspection = name[:-5]
    with open(os.path.join(out, name)) as f:
        for p in json.load(f).get("problems", []):
            path = re.sub(r"^.*?\$PROJECT_DIR\$/", "", p.get("file", ""))
            if path.startswith("build/"):
                skipped["generated build output"] += 1
            elif inspection in known:
                skipped[inspection] += 1
            elif (path, inspection) in ACCEPTED:
                skipped["accepted"] += 1
            else:
                message = re.sub(r"<[^>]+>", "", p.get("description", "")).replace("#loc", "").strip()
                problems.append((path, int(p.get("line") or 0), inspection, message))

for path, line, inspection, message in sorted(problems):
    print(f"{path}:{line}  [{inspection}] {message}")
print(f"\n{len(problems)} problem(s)." if problems else "\nNo problems.")
if skipped:
    print("Not listed (known not to apply, see the top of this script): "
          + ", ".join(f"{n} {k}" for k, n in sorted(skipped.items())))
EOF
