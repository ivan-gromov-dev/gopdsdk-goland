# Changelog

## Unreleased

## 0.2.0 — 2026-09-15

- Added a Project Health surface for structured doctor/probe readiness,
  project prerequisites, focused remediation, and raw support output.
- Added a cancellable New Playdate Game wizard backed by `gopdsdk init`, with
  project opening and a first-Simulator-run prompt after successful creation.
- Added cancellable Playdate Simulator build and run configurations.
- Added Simulator actions to the toolbar, Run menu, and Playdate tool window.
- Added analysis/execution target status, structured progress handling, and
  editor diagnostics for structured build failures.

SDK and Simulator readiness is reported only from the corresponding structured
external evidence. No USB or physical-device readiness is claimed by this
release.

All notable user-visible changes are recorded here. Release claims distinguish
plugin-unit, editor-integration, SDK, Simulator, USB, and physical-device
evidence.

## 0.1.0 — 2026-09-13

- Add a GoLand-only native LSP integration for `gopdsdk lsp`.
- Add project settings for executable discovery and analyzer configuration.
- Add bounded compatibility probing, refresh, restart, logs, and troubleshooting.
- Expose analyzer diagnostics, related locations, rule help, and edit-only safe
  quick fixes through the native IntelliJ LSP client.
- Add deterministic reliability coverage and GoLand 2026.1.4–2026.2 verification.
- Add signed, reproducible release packaging with SBOM and provenance gates.

No SDK, Simulator, USB, or physical-device readiness is claimed by this release.
