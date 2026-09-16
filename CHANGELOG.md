# Changelog

## Unreleased

- Completed M11's Playdate Overview: active module and analysis target,
  explicitly refreshed project health, SDK/analyzer protocol versions, shared
  operation state, and module Problems counts from the IDE's existing collector.
- Reused build/run, analyzer, device, and log actions, with shortcuts to native
  Problems and Run output. Health refreshes reject stale module results and
  expose unchecked, loading, failure, and cancellation states.
- Routed Simulator actions and build error locations through the selected Go
  module instead of always using the project root. Health checks are also
  module-scoped and no longer run automatically when the tool window opens.
- Explicitly label the gopdsdk release version as unavailable: the existing CLI
  only provides the analyzer protocol version, not a release-version contract.

Evidence: Windows Kotlin compilation and focused plugin-unit tests. Full CI,
Plugin Verifier, live editor accessibility, and SDK/device acceptance remain
external gates; no device logs were read.

- Implemented M10 device actions for the selected Go module: connection probe,
  build, install/run, explicit crash/error logs, Data Disk mount, and safe eject
  with CLI-confirmed USB reconnection.
- Added capability negotiation, cancellable per-project serialized operations,
  distinct CLI progress stages, and last-explicit-operation device status.
- Device logs open in read-only memory-backed editor tabs, only on request.
  Failed runs offer explicit log actions and never read logs automatically.
- Added focused plugin-unit fixtures for device contracts, cancellation,
  connection evidence, progress framing, log content, and action registration.

Evidence: local Kotlin compilation and focused plugin-unit checks. Full builds,
cross-platform compatibility checks and Plugin Verifier are left to CI. Live
editor, device-build, USB and physical-device acceptance remain unverified.

## 0.3.0 — 2026-09-15

- Removed generated Kotlin compatibility bridges to deprecated/experimental
  IntelliJ interface methods reported by Marketplace for 0.2.0. Build diagnostic
  refresh now uses the supported daemon restart overload with an explicit reason.
- Added M9 Analyzer Configuration and Findings for the selected Go module:
  target/profile selection, analyzer-owned rule catalog, exact-version LSP help,
  rule enable/exclude controls, and severity overrides.
- Matched VS Code's analyzer settings projection, including catalog-derived
  experimental profiles, with persistent per-module LSP settings and clients.
- Added reasoned suppressions from editor diagnostics and comparison findings,
  with analyzer policy checks, stale-source rejection, and undoable edits.
- Added baseline create/update/inspect/validate and shared/Simulator/device
  comparison through the versioned gopdsdk CLI contracts.

Evidence: focused plugin-unit tests and Kotlin compilation on Windows. Full
compatibility/OS CI and live editor parity remain external evidence; no SDK,
Simulator, USB, or physical-device claim is made.

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
