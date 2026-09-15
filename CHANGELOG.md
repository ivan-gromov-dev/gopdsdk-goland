# Changelog

## Unreleased

- Added cancellable Playdate Simulator build and run configurations.
- Added Simulator actions to the toolbar, Run menu, and Playdate tool window.
- Added analysis/execution target status, structured progress handling, and
  editor diagnostics for structured build failures.

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
