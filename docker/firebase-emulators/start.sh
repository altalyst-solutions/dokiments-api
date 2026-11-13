#!/usr/bin/env bash
#
# Starts the Firebase emulators (Auth, Firestore and the Emulator UI) for local development and
# keeps their data between restarts.
#
# The emulators only keep data in memory, so this script saves it through the Firebase CLI:
#
# - on start, it loads the most recent save from /data/exports, if there is one;
# - every SAVE_INTERVAL_SECONDS while running, so a crash, a forced quit of Docker or a laptop
#   restart loses at most that much;
# - once more when the container is stopped.
#
# Only the most recent KEEP_SAVES saves are kept, so they cover just the last few minutes: a mistake,
# such as clearing the accounts in the Emulator UI, reaches every save within minutes. So the save
# loaded at each start is also kept as a backup, for the last KEEP_BACKUPS starts. A backup can be
# restored with the commands below.
#
# /data is a Docker volume (see compose.yaml), so the data survives `docker compose down` and is
# removed with `docker compose down --volumes`.
#
# Commands, with the emulators stopped (`docker compose stop firebase`):
#
#   docker compose run --rm firebase backups          lists the backups
#   docker compose run --rm firebase restore <name>   loads that backup at the next start
#
# Each emulator listens on the same port inside the container as on the host, because the
# Emulator UI runs in the browser and connects to the emulators at the ports it is told.

set -euo pipefail

readonly PROJECT_ID="demo-dokiments"
readonly EXPORTS_DIR=/data/exports
readonly BACKUPS_DIR=/data/backups
readonly SAVE_INTERVAL_SECONDS="${SAVE_INTERVAL_SECONDS:-60}"
readonly KEEP_SAVES="${KEEP_SAVES:-3}"
readonly KEEP_BACKUPS="${KEEP_BACKUPS:-5}"
readonly FIRESTORE_PORT="${FIRESTORE_PORT:-8085}"
readonly AUTH_PORT="${AUTH_PORT:-9099}"
readonly UI_PORT="${UI_PORT:-4000}"
# Which emulators to start. The integration tests leave out the UI, which they don't use.
readonly EMULATORS="${EMULATORS:-auth,firestore,ui}"
# The Firestore page of the Emulator UI streams changes to the browser over this port.
readonly WEBSOCKET_PORT="${WEBSOCKET_PORT:-9150}"
# The Logs page of the Emulator UI streams the emulators' logs over this port.
readonly LOGGING_PORT="${LOGGING_PORT:-4500}"
# Used inside the container only: the CLI's coordinator, through which saves are made.
readonly HUB_PORT=4400
# The CLI itself rather than `npx firebase`: npx doesn't pass signals on, so a stop would never
# reach the emulators and Docker would have to kill them.
readonly FIREBASE=./node_modules/.bin/firebase

log() {
  echo "[firebase-emulators] $*"
}

# These come from .env through compose.yaml, so check them before using them. A KEEP_SAVES of 0
# in particular would make the clean-up below delete every save.
for setting in SAVE_INTERVAL_SECONDS KEEP_SAVES KEEP_BACKUPS FIRESTORE_PORT AUTH_PORT UI_PORT WEBSOCKET_PORT LOGGING_PORT; do
  if [[ ! "${!setting}" =~ ^[1-9][0-9]*$ ]]; then
    log "${setting} must be a whole number of at least 1, but is '${!setting}'. Check .env."
    exit 1
  fi
done

# Each save is a folder named save_<timestamp>, so the newest sorts last. A backup is a copy of
# one, named backup_<the same timestamp>, so its name says when its data was saved.
latest_save() {
  find "$EXPORTS_DIR" -mindepth 1 -maxdepth 1 -type d -name 'save_*' | sort | tail -n 1
}

# Keeps a copy of a save as a backup, unless it is kept already, and removes all but the newest
# KEEP_BACKUPS backups.
back_up() {
  local backup
  backup="${BACKUPS_DIR}/backup_$(basename "$1" | sed 's/^save_//')"
  if [[ ! -d "$backup" ]]; then
    cp -R "$1" "${backup}.partial" && mv "${backup}.partial" "$backup"
    log "Backed up the data as $(basename "$backup")"
  fi
  find "$BACKUPS_DIR" -mindepth 1 -maxdepth 1 -type d -name 'backup_*' | sort | head -n "-${KEEP_BACKUPS}" |
    xargs -r rm -rf
}

list_backups() {
  local backups
  backups="$(find "$BACKUPS_DIR" -mindepth 1 -maxdepth 1 -type d -name 'backup_*' | sort -r)"
  if [[ -z "$backups" ]]; then
    log "No backups yet. One is kept each time the emulators start with saved data."
    return
  fi
  log "Backups, newest first (the name is when the data was saved, in UTC):"
  while read -r backup; do
    echo "  $(basename "$backup")"
  done <<<"$backups"
}

# Makes a backup the newest save, so the next start loads it. The data it replaces is backed up
# first, so a restore can be undone the same way.
restore() {
  local backup="${BACKUPS_DIR}/${1:-}"
  if [[ -z "${1:-}" || "$1" == */* || ! -d "$backup" ]]; then
    log "No backup named '${1:-}'."
    list_backups
    exit 1
  fi
  local current
  current="$(latest_save)"
  if [[ -n "$current" ]]; then
    back_up "$current"
  fi
  local target
  target="${EXPORTS_DIR}/save_$(date -u +%Y%m%dT%H%M%S%N)"
  cp -R "$backup" "${target}.partial" && mv "${target}.partial" "$target"
  log "Restored $1. Start the emulators to load it: docker compose start firebase"
}

mkdir -p "$EXPORTS_DIR" "$BACKUPS_DIR"

case "${1:-}" in
  "") ;;
  backups)
    list_backups
    exit 0
    ;;
  restore)
    restore "${2:-}"
    exit 0
    ;;
  *)
    log "Unknown command '$1'. Use 'backups' or 'restore <name>'."
    exit 1
    ;;
esac

# The emulators' addresses. Written here rather than kept as a file so the ports can be changed
# in .env. Listening on 0.0.0.0 makes them reachable from outside the container.
cat > firebase.json <<JSON
{
  "emulators": {
    "singleProjectMode": true,
    "auth": { "host": "0.0.0.0", "port": ${AUTH_PORT} },
    "firestore": { "host": "0.0.0.0", "port": ${FIRESTORE_PORT}, "websocketPort": ${WEBSOCKET_PORT} },
    "hub": { "host": "0.0.0.0", "port": ${HUB_PORT} },
    "ui": { "enabled": true, "host": "0.0.0.0", "port": ${UI_PORT} },
    "logging": { "host": "0.0.0.0", "port": ${LOGGING_PORT} }
  }
}
JSON

save() {
  local target
  target="${EXPORTS_DIR}/save_$(date -u +%Y%m%dT%H%M%S%N)"
  if "$FIREBASE" emulators:export "$target" --project "$PROJECT_ID" --force >/dev/null 2>&1; then
    # Remove all but the newest KEEP_SAVES saves.
    find "$EXPORTS_DIR" -mindepth 1 -maxdepth 1 -type d -name 'save_*' | sort | head -n "-${KEEP_SAVES}" |
      xargs -r rm -rf
  else
    rm -rf "$target"
    log "Saving the data failed; the previous save is kept."
  fi
}

import_args=()
previous="$(latest_save)"
if [[ -n "$previous" ]]; then
  log "Loading saved data from ${previous}"
  back_up "$previous"
  import_args=("--import=${previous}")
else
  log "No saved data yet, starting with an empty database"
fi

"$FIREBASE" emulators:start --project "$PROJECT_ID" --only "$EMULATORS" "${import_args[@]}" &
emulators_pid=$!

# Called by the trap below when Docker stops the container.
# shellcheck disable=SC2329
stop() {
  log "Stopping: saving the data first"
  save
  kill -INT "$emulators_pid" 2>/dev/null || true
  wait "$emulators_pid" 2>/dev/null || true
  log "Saved and stopped"
  exit 0
}
trap stop TERM INT

# Ready once every emulator answers, not just the coordinator, which starts first.
ready() {
  curl -fsS -o /dev/null "http://localhost:${HUB_PORT}/emulators" &&
    curl -fsS -o /dev/null "http://localhost:${AUTH_PORT}/" &&
    curl -fsS -o /dev/null "http://localhost:${FIRESTORE_PORT}/" &&
    { [[ "$EMULATORS" != *ui* ]] || curl -fsS -o /dev/null "http://localhost:${UI_PORT}/"; }
}

until ready; do
  if ! kill -0 "$emulators_pid" 2>/dev/null; then
    log "The emulators exited during start-up"
    exit 1
  fi
  sleep 1
done
log "Ready: ${EMULATORS} (Auth on port ${AUTH_PORT}, Firestore on ${FIRESTORE_PORT}, UI on ${UI_PORT}); saving every ${SAVE_INTERVAL_SECONDS}s"

# `sleep` runs in the background and is waited for, so a stop signal is handled right away
# instead of after the current sleep.
while kill -0 "$emulators_pid" 2>/dev/null; do
  sleep "$SAVE_INTERVAL_SECONDS" &
  wait $! || true
  save
done

log "The emulators exited unexpectedly"
exit 1
