# Git workflow

This repo follows Gitflow with Conventional Commits (ADR-0023).

## Before starting any work on an issue

1. Make sure you are on `develop` and it is up to date:
   ```
   git checkout develop && git pull
   ```
2. Create a new branch following the naming convention:
   ```
   git checkout -b feat/<issue-number>-<description-kebab-case>
   ```
   Use `fix/` instead of `feat/` for bug fixes.

   Examples:
   - `feat/3-scaffold-gradle-build`
   - `fix/87-regression-attribut-vitesse`

3. Do all work on that branch. Never commit directly to `develop` or `main`.

## Commit messages — Conventional Commits

Format: `type(scope): description (#issue)`

Valid scopes: `simulator`, `webapp`

Valid types: `feat`, `fix`, `chore`, `refactor`, `docs`, `test`, `perf`

Examples:
- `feat(simulator): add protobuf build scaffold (#3)`
- `fix(webapp): correct cache path in CI (#5)`

A `feat!` or `BREAKING CHANGE` in the footer triggers a major version bump at release time.

## Opening a pull request

Target: `develop` (never `main` directly).

The PR body must include `Closes #<issue-number>` so GitHub closes the issue automatically on merge.

## Branch protection

Direct pushes to `main` and `develop` are blocked. All changes go through a PR.
