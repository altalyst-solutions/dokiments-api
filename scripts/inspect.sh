#!/usr/bin/env bash
#
# Runs IntelliJ IDEA's code inspections on the whole project from the command line and lists
# the problems by file, the same ones the editor shows. It covers what the build itself can't
# check: Spring configuration, YAML, Gradle scripts, docs.
#
#   scripts/inspect.sh
#
# It is meant mainly for AI agents, which can't see the editor, so they can find and fix these
# problems as they work. Developers see the same findings in IntelliJ's Problems view, and can
# run this too.
#
# It exits with 1 when it finds problems, and with 2 when IntelliJ isn't found or couldn't
# inspect part of the project, so a run that silently checked less is never mistaken for a
# clean one.
#
# Needs IntelliJ IDEA installed and the project opened in it at least once (so it knows which
# JDK the project uses). It runs with its own temporary settings, so it works while the IDE is
# open and doesn't change your IDE settings. It looks for IntelliJ in its usual places on macOS
# and Linux, including installs made with the JetBrains Toolbox. Set IDEA_HOME to IntelliJ's
# install folder (on macOS, the app's Contents folder) if it isn't found, and IDEA_CONFIG if
# your IDE settings folder isn't found. In WSL, IntelliJ runs on the Windows side and can't be
# used from here.
#
# Some findings are known not to apply here and are listed separately at the end instead of
# with the problems:
# - British spellings: the project is written in British English, which Grazie flags as not
#   American ("colour", "behaviour"). Every other Grazie finding is listed.
# - Inputs of GitHub Actions: this command-line run can't download each action's description,
#   so every input looks undefined. The editor resolves them.
# - Anything under build/, which is generated.
# - A few specific findings that are intentional (see ACCEPTED below for each reason).

set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# The first IntelliJ install found in the usual places, newest version first where there are
# several (the JetBrains Toolbox keeps one folder per version on Linux).
find_idea_home() {
  local candidate
  for candidate in \
    "/Applications/IntelliJ IDEA.app/Contents" \
    "/Applications/IntelliJ IDEA Ultimate.app/Contents" \
    "/Applications/IntelliJ IDEA CE.app/Contents" \
    "$HOME/Applications/IntelliJ IDEA.app/Contents" \
    "$HOME/Applications/IntelliJ IDEA Ultimate.app/Contents" \
    "$HOME/Applications/IntelliJ IDEA CE.app/Contents" \
    $(compgen -G "$HOME/.local/share/JetBrains/Toolbox/apps/intellij-idea*" | sort -rV) \
    /snap/intellij-idea/current \
    /snap/intellij-idea-ultimate/current \
    /snap/intellij-idea-community/current \
    /opt/idea*; do
    if [[ -x "$candidate/bin/inspect.sh" ]]; then
      echo "$candidate"
      return
    fi
  done
}
idea_home="${IDEA_HOME:-$(find_idea_home)}"
inspect="${idea_home}/bin/inspect.sh"

if [[ -z "$idea_home" || ! -x "$inspect" ]]; then
  echo "IntelliJ IDEA not found${idea_home:+ at ${idea_home}}." >&2
  echo "Set IDEA_HOME to its install folder (on macOS, the app's Contents folder)." >&2
  exit 2
fi

# The newest IntelliJ settings folder, for the JDK definitions below.
find_ide_config() {
  local base
  for base in "$HOME/Library/Application Support/JetBrains" "$HOME/.config/JetBrains"; do
    if [[ -d "$base" ]]; then
      find "$base" -mindepth 1 -maxdepth 1 -type d \( -name 'IntelliJIdea*' -o -name 'IdeaIC*' \) | sort -V | tail -n 1
      return
    fi
  done
}
ide_config="${IDEA_CONFIG:-$(find_ide_config)}"

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir:?}"' EXIT
mkdir -p "$work_dir"/{config/options,system,log,out}

# Separate settings and caches, so this isn't blocked by (and doesn't touch) a running IDE.
cat > "$work_dir/idea.properties" <<EOF
idea.config.path=${work_dir}/config
idea.system.path=${work_dir}/system
idea.log.path=${work_dir}/log
EOF

# The project refers to its JDK by the name it has in your IDE (for example "25"). Without that
# definition, IntelliJ can't resolve even basic Java types and most Java checks find nothing, so
# only the JDK definitions are copied from your IDE settings. Nothing else is read.
if [[ -n "$ide_config" && -f "$ide_config/options/jdk.table.xml" ]]; then
  cp "$ide_config/options/jdk.table.xml" "$work_dir/config/options/"
else
  echo "No IntelliJ JDK definitions found (looked in ${ide_config:-the default settings folders})." >&2
  echo "Open the project in IntelliJ once, or set IDEA_CONFIG to your IDE settings folder." >&2
  exit 2
fi

# IntelliJ's default inspections, as in a fresh editor, except one: the "extract method"
# recommender crashes on records with a compact constructor. In this command-line mode such a
# crash abandons the whole file, so every other check of that file would be lost with it.
cat > "$work_dir/profile.xml" <<'EOF'
<profile version="1.0">
  <option name="myName" value="Default" />
  <inspection_tool class="ExtractMethodRecommender" enabled="false" level="INFORMATION" enabled_by_default="false" />
</profile>
EOF

echo "Running IntelliJ inspections (about a minute)..."
if ! IDEA_PROPERTIES="$work_dir/idea.properties" "$inspect" "$project_dir" "$work_dir/profile.xml" \
  "$work_dir/out" -v0 -format json > "$work_dir/run.log" 2>&1; then
  echo "IntelliJ couldn't inspect the project:" >&2
  tail -n 20 "$work_dir/run.log" >&2
  exit 2
fi

python3 - "$work_dir/out" "$work_dir/log/idea.log" <<'EOF'
import json, os, re, sys
from collections import Counter

out, ide_log = sys.argv[1], sys.argv[2]

# Grazie's suggestions to use American instead of British spelling.
BRITISH_SPELLING = re.compile(r"American English|British (roots|English)")

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
    # Email layout: mail clients, Outlook above all, ignore CSS for table layout and need the
    # old width, align, valign and cellpadding attributes to lay an email out at all.
    ("src/main/resources/email/layout.html", "HtmlDeprecatedAttribute"),
}

problems, skipped = [], Counter()
for name in sorted(os.listdir(out)):
    if not name.endswith(".json"):
        continue
    inspection = name[:-5]
    with open(os.path.join(out, name)) as f:
        for p in json.load(f).get("problems", []):
            path = re.sub(r"^.*?\$PROJECT_DIR\$/", "", p.get("file", ""))
            message = re.sub(r"<[^>]+>", "", p.get("description", "")).replace("#loc", "").strip()
            if path.startswith("build/"):
                skipped["generated build output"] += 1
            elif inspection == "UndefinedParamsPresent":
                skipped["GitHub Actions inputs"] += 1
            elif inspection.startswith("Grazie") and BRITISH_SPELLING.search(message):
                skipped["British spellings"] += 1
            elif (path, inspection) in ACCEPTED:
                skipped["accepted"] += 1
            else:
                problems.append((path, int(p.get("line") or 0), inspection, message))

# A file IntelliJ failed to inspect looks exactly like a clean one in the results, so the log
# is checked for such failures and they are reported as errors.
not_inspected = set()
if os.path.exists(ide_log):
    with open(ide_log, errors="replace") as f:
        for line in f:
            match = re.search(r"SEVERE .*GlobalInspectionContextImpl - In file: (\S+)", line)
            if match:
                not_inspected.add(match.group(1).split("/dokiments-api/", 1)[-1])

for path, line, inspection, message in sorted(problems):
    print(f"{path}:{line}  [{inspection}] {message}")
print(f"\n{len(problems)} problem(s)." if problems else "\nNo problems.")
if skipped:
    print("Not listed (known not to apply, see the top of this script): "
          + ", ".join(f"{n} {k}" for k, n in sorted(skipped.items())))
if not_inspected:
    print("\nIntelliJ failed while inspecting these files, so their results are incomplete:", file=sys.stderr)
    for path in sorted(not_inspected):
        print(f"  {path}", file=sys.stderr)
    sys.exit(2)
sys.exit(1 if problems else 0)
EOF
