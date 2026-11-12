<!--
PR title: Conventional Commits, because it becomes the merge commit's title.
For example: feat(newsletter): add subscribe endpoint
-->

## Summary

<!-- What this pull request changes and why. Link related issues or pull requests. -->

## Changes

-

## How it was tested

- [ ] `./gradlew build` passes locally (formatting, static analysis, unit, architecture and integration tests)
- [ ] New or changed behaviour is covered by tests, and a fix has a test that fails without it
- Manual checks:

## Impact

<!-- Keep the option that applies and describe any change. The web app depends on the first two. -->

- **API contract:** no change / changed (paths, request or response bodies, status codes, `{ "error": "..." }` shape; see the diff of `docs/openapi.yaml`)
- **Firestore data:** no change / changed (collections, field names, field types)
- **Configuration:** no change / new or changed environment variables (documented in `docs/configuration.md`)
- **Security:** no change / new public endpoint, authentication or permission change

## Checklist

- [ ] The PR title and every commit follow Conventional Commits
- [ ] `docs/` and `.github/copilot-instructions.md` are updated if conventions, commands or configuration changed

## Notes for reviewers

<!-- Anything that needs a closer look, open questions, or follow-up work. -->
