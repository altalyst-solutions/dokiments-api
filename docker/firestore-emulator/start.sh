#!/usr/bin/env bash
#
# Starts the Firestore emulator for local development and keeps its data between restarts.
#
# The emulator only keeps data in memory. Its own --export-on-exit option doesn't save
# anything when the container is stopped (neither on SIGTERM, SIGINT nor its /shutdown
# endpoint), so this script saves the data itself through the emulator's export endpoint:
#
# - on start, it loads the most recent save from /data/exports, if there is one;
# - every SAVE_INTERVAL_SECONDS while running, so a crash, a forced quit of Docker or a
#   laptop restart loses at most that much;
# - once more when the container is stopped.
#
# Only the most recent KEEP_SAVES saves are kept. /data is a Docker volume (see
# compose.yaml), so the data survives `docker compose down` and is removed with
# `docker compose down --volumes`.

set -euo pipefail

readonly PROJECT_ID="demo-dokiments"
readonly PORT=8080
readonly EXPORTS_DIR=/data/exports
readonly SAVE_INTERVAL_SECONDS="${SAVE_INTERVAL_SECONDS:-60}"
readonly KEEP_SAVES="${KEEP_SAVES:-3}"
readonly EMULATOR_JAR=/google-cloud-sdk/platform/cloud-firestore-emulator/cloud-firestore-emulator.jar

log() {
  echo "[firestore-emulator] $*"
}

# Both settings come from .env through compose.yaml, so check them before using them. A
# KEEP_SAVES of 0 in particular would make the clean-up below delete every save.
for setting in SAVE_INTERVAL_SECONDS KEEP_SAVES; do
  if [[ ! "${!setting}" =~ ^[1-9][0-9]*$ ]]; then
    log "${setting} must be a whole number of at least 1, but is '${!setting}'. Check FIRESTORE_${setting} in .env."
    exit 1
  fi
done

# Each save is a folder named firestore_export_<timestamp>, so the newest sorts last.
latest_save() {
  find "$EXPORTS_DIR" -mindepth 1 -maxdepth 1 -type d -name 'firestore_export_*' | sort | tail -n 1
}

save() {
  # The export endpoint fails unless the target folder already exists.
  mkdir -p "$EXPORTS_DIR"
  if curl -fsS -o /dev/null -X POST "http://localhost:${PORT}/emulator/v1/projects/${PROJECT_ID}:export" \
    -H 'Content-Type: application/json' \
    -d "{\"database\":\"projects/${PROJECT_ID}/databases/(default)\",\"export_directory\":\"${EXPORTS_DIR}\"}"; then
    # Remove all but the newest KEEP_SAVES saves.
    find "$EXPORTS_DIR" -mindepth 1 -maxdepth 1 -type d -name 'firestore_export_*' | sort | head -n "-${KEEP_SAVES}" |
      xargs -r rm -rf
  else
    log "Saving the data failed; the previous save is kept."
  fi
}

mkdir -p "$EXPORTS_DIR"

import_args=()
previous="$(latest_save)"
if [[ -n "$previous" ]]; then
  log "Loading saved data from ${previous}"
  import_args=("--import-data=${previous}/$(basename "$previous").overall_export_metadata")
else
  log "No saved data yet, starting with an empty database"
fi

# The emulator's own launcher is a bash script that doesn't pass signals on, so Java is
# started directly. It runs in the background so this script can save and stop it.
java -cp "$EMULATOR_JAR" com.google.cloud.datastore.emulator.firestore.CloudFirestore start \
  --host=0.0.0.0 --port="$PORT" "${import_args[@]}" &
emulator_pid=$!

# Called by the trap below when Docker stops the container.
# shellcheck disable=SC2329
stop() {
  log "Stopping: saving the data first"
  save
  kill -TERM "$emulator_pid" 2>/dev/null || true
  wait "$emulator_pid" 2>/dev/null || true
  log "Saved and stopped"
  exit 0
}
trap stop TERM INT

until curl -fsS -o /dev/null "http://localhost:${PORT}/"; do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    log "The emulator exited during start-up"
    exit 1
  fi
  sleep 1
done
log "Ready on port ${PORT}, saving every ${SAVE_INTERVAL_SECONDS}s"

# `sleep` runs in the background and is waited for, so a stop signal is handled right away
# instead of after the current sleep.
while kill -0 "$emulator_pid" 2>/dev/null; do
  sleep "$SAVE_INTERVAL_SECONDS" &
  wait $! || true
  save
done

log "The emulator exited unexpectedly"
exit 1
