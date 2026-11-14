#!/usr/bin/env bash
#
# Fills the local Firestore emulator with the template catalog, so GET /api/templates and saving templates can be
# tried locally. The catalog is scripts/local-catalog.json: a copy of the styles and document types the web app
# offers, from which the same documents are written as in production:
#
# - templateStyles/<style>, with sortOrder in the order of the file;
# - templates/<style>-<document type> for every style and document type, with the style's tier and sortOrder
#   counting through the styles, then the document types, in the order of the file.
#
# createdAt is left out, as for templates added before it was recorded. Running it again writes the same documents
# again, replacing any local changes to them; other documents are left alone. It only ever writes to the emulator:
# it runs inside the emulators' container, against the emulator's own address.
#
# Usage, with the emulators running (./gradlew bootRun, or docker compose up -d):
#
#   scripts/seed-local-catalog.sh

set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -z "$(docker compose ps --status running --quiet firebase 2>/dev/null)" ]]; then
  echo "The Firebase emulators aren't running. Start them with ./gradlew bootRun or docker compose up -d first." >&2
  exit 1
fi

# Builds Firestore's commit request from the plain catalog on standard input and sends it to the emulator. One
# commit writes every document or none. The emulator accepts "Bearer owner" as its administrator, which no real
# Firestore does.
# shellcheck disable=SC2016 # The JavaScript below is for Node, not for the shell to expand.
readonly SEED='
const chunks = [];
for await (const chunk of process.stdin) chunks.push(chunk);
const catalog = JSON.parse(Buffer.concat(chunks).toString("utf8"));
const documents = "projects/demo-dokiments/databases/(default)/documents";
const text = (value) => ({ stringValue: value });
const whole = (value) => ({ integerValue: String(value) });
const writes = catalog.styles.map((style, index) => ({
  update: {
    name: `${documents}/templateStyles/${style.id}`,
    fields: {
      name: text(style.name),
      description: text(style.description),
      tier: text(style.tier),
      sortOrder: whole(index),
      isActive: { booleanValue: true },
    },
  },
}));
let sortOrder = 0;
for (const style of catalog.styles) {
  for (const type of catalog.documentTypes) {
    writes.push({
      update: {
        name: `${documents}/templates/${style.id}-${type.type}`,
        fields: {
          name: text(type.name),
          description: text(type.description),
          documentType: text(type.type),
          styleId: text(style.id),
          tier: text(style.tier),
          sortOrder: whole(sortOrder++),
          isActive: { booleanValue: true },
          fields: {
            arrayValue: {
              values: type.fields.map((field) => ({
                mapValue: {
                  fields: {
                    key: text(field.key),
                    label: text(field.label),
                    ...(field.placeholder ? { placeholder: text(field.placeholder) } : {}),
                    type: text(field.type),
                  },
                },
              })),
            },
          },
        },
      },
    });
  }
}
const port = process.env.FIRESTORE_PORT || "8085";
const answer = await fetch(`http://localhost:${port}/v1/${documents}:commit`, {
  method: "POST",
  headers: { "Content-Type": "application/json", Authorization: "Bearer owner" },
  body: JSON.stringify({ writes }),
});
if (!answer.ok) {
  console.error(`The emulator refused the catalog (${answer.status}): ${await answer.text()}`);
  process.exit(1);
}
const templates = catalog.styles.length * catalog.documentTypes.length;
console.log(`Wrote ${catalog.styles.length} styles and ${templates} templates to the local emulator.`);
'

docker compose exec -T firebase node --input-type=module --eval "$SEED" < scripts/local-catalog.json
