# Development roadmap

This roadmap validates `gopdsdk lsp` through a thin GoLand client. Plugin/unit
evidence is distinct from live editor, SDK, Simulator, USB, and device evidence.

The 1.0.0 release freezes the implemented M0–M11 feature set. Its source
snapshot passed the full CI matrix and Plugin Verifier; durable evidence and
release limitations are recorded in [CHANGELOG.md](CHANGELOG.md#100--2026-09-16).
Remaining work is live editor/reliability/accessibility acceptance, SDK and
physical-device validation, and distribution evidence described below. Release
packaging and Marketplace publication are separate gates in [RELEASING.md](RELEASING.md).

## M0 — Scaffold

Status: complete.

- Kotlin/JVM and IntelliJ Platform Gradle Plugin 2.x;
- GoLand and bundled Go-plugin dependencies;
- native project-wide LSP integration for Go files;
- plugin metadata and verification configuration.

## M1 — Reproducible bootstrap

Status: complete.

- dependency verification data is checked in;
- GoLand 2026.1.4 is the pinned compilation floor and CI also tests 2026.2.0.1;
- Windows, macOS, and Linux run the build and tests in CI;
- Plugin Verifier checks both supported compatibility boundaries in CI.

## M2 — Executable contract

Status: complete.

- project settings and deterministic executable discovery;
- bounded version/capability probe with actionable notifications;
- restart after settings changes, clean shutdown, and redacted logs;
- test missing, old, incompatible, crashing, and cleanly exiting servers.

## M3 — Analyzer configuration

Status: complete; diagnostic parity depends on the selected server honoring the
corresponding analyzer-protocol fields.

- [x] target, SDK floor, rules/categories, severity, baseline, changed-file mode,
  and explicit deep-analysis opt-in;
- [x] project persistence and consistent settings across project content roots;
- [x] configuration refresh without restart when supported.

Verification: parity with equivalent `gopdsdk check` executions.

## M4 — Diagnostic UX

Status: complete; manual cross-platform editor smoke confirmation remains.

- [x] related locations, stable rule identifiers, and rule help;
- [x] preview and apply only analyzer-provided safe fixes;
- [x] restart, refresh, logs, and troubleshooting actions;
- [x] graceful coexistence with the bundled Go plugin.

Verification: platform and sandboxed UI tests for edits, saves, stale versions,
clearing, cancellation, fixes, and multi-module projects.

The plugin delegates diagnostic rendering, related locations, versioned
documentation links, document synchronization, cancellation, stale-version
handling, edit previews, and edit application to the native IntelliJ LSP
client. Its customization rejects command-backed, foreign, and non-quick-fix
actions, so only analyzer-provided edit-only safe fixes are exposed. The Tools
menu adds refresh, restart, Language Services logs, and troubleshooting actions;
the plugin continues to own no general Go language feature alongside the
bundled Go plugin.

## M5 — Reliability

Status: implementation complete; cross-platform CI and live desktop evidence remain.

- [x] add deterministic reload, configuration-churn, crash, and shutdown stress;
- [x] define reproducible startup, incremental latency, memory, and cancellation
  measurements on a realistic game; keep deep analysis off by default until
  evidence supports it;
- [ ] complete live smoke evidence on all three desktop platforms.

Verification: automated stress sessions and manual smoke checks on Windows,
macOS, and Linux, labeled as editor-integration evidence.

The deterministic plugin test models a 603-file, three-module game, performs 40
rapid configuration changes, and runs 60 startup/reload cycles with six forced
analyzer crashes. It verifies clean shutdown and process reaping, records the
bounded probe latency as structured plugin-unit evidence, and protects the
deep-analysis opt-in default. CI runs this coverage against both supported
GoLand boundaries on all three desktop platforms. Native IntelliJ LSP owns
document synchronization, cancellation, crash recovery, and project reload;
manual smoke results for those behaviors must name the GoLand build and host
platform and must not be described as SDK, Simulator, USB, or device evidence.
The reproducible live protocol is documented in [RELIABILITY.md](RELIABILITY.md).

## M6 — Distribution

Status: implementation complete; live desktop evidence, real screenshots,
hosted attestations, and Marketplace publication require external release
evidence.

- [x] plugin icon, changelog, privacy, security, support, and release policies;
- [x] signed CI artifacts, strict dependency checksum verification, SBOM,
  provenance, and reproducible unsigned ZIP verification;
- [x] guarded EAP and stable JetBrains Marketplace publication workflow;
- [x] GoLand-only product dependency and supported-range release protocol;
- [ ] capture real product screenshots and complete live installation, upgrade,
  downgrade, EAP, and stable publication evidence.

The first release workflow run accepts only the immutable `v0.1.0` tag, creates a
developer-signed ZIP, verifies its signature, emits SHA-256 and SPDX SBOM
artifacts, and requests GitHub provenance attestations. Publication is a
separate boolean input protected by the `marketplace-eap` or
`marketplace-stable` GitHub environment. Secrets are never committed. Because
Marketplace forbids duplicate versions, `0.1.0` is the EAP candidate and the
first stable publication uses the next reviewed patch version.

Exit criterion: GoLand, VS Code, and CLI expose equivalent rule identifiers,
diagnostics, and safe fixes without analysis rules in editor clients.

## M7 — Simulator workflow

Status: implemented. Automated plugin-unit and compatibility verification cover
the command contract and UI registrations. Live SDK-integration evidence remains
a release gate on each claimed host platform.

- build the active application for Simulator;
- build and launch it in Playdate Simulator;
- expose the active Simulator/device analysis target in the status bar;
- contribute cancellable GoLand run configurations with visible progress and
  output;
- convert structured build failures with source locations into GoLand
  diagnostics;
- offer focused build/run actions from the toolbar, Run menu, and Playdate tool
  window.

The implementation contributes saved and temporary Playdate Simulator run
configurations, delegates only to the versioned structured `build`/`run`
contracts, maps progress into the status bar and Run console, and publishes
source locations through an external annotator. Process ownership and
cancellation remain with the IntelliJ execution framework and gopdsdk.

The plugin must invoke `gopdsdk build` and `gopdsdk run`; it must not reproduce
build plans, SDK discovery, packaging, or launch policy. An initial slice may
use console-backed processes. Rich progress, artifact discovery, and reliable
build diagnostics require a stable machine-readable CLI result from gopdsdk.

Verification: plugin tests with deterministic command fixtures, plus manual
SDK-integration smoke tests that build and launch an external game in the
official Simulator on every claimed host platform.

## M8 — Project health and creation

Status: implementation complete; plugin-unit CI and external readiness evidence remain.

- present gopdsdk, analyzer-protocol, Playdate SDK, Simulator, device-toolchain,
  USB connection, module, manifest, and analyzer-configuration readiness in one
  Project Health surface;
- attach focused remediation actions to failed checks;
- create a new game through a guided wrapper around `gopdsdk init`, then open
  the generated project and offer its first Simulator run;
- keep raw troubleshooting output available for support.

The health view consumes `gopdsdk doctor` and the relevant `gopdsdk probe`
commands. A stable structured doctor/probe report is required before the view
may classify individual checks or attach remediation actions; parsing prose is
not an accepted integration contract. The project wizard may ship against the
existing structured command arguments, provided success and the created path
can be identified without parsing incidental log text.

Verification: unit and sandboxed platform tests for every health state and
wizard cancel/failure path; SDK, Simulator, USB, and device readiness claims
require their corresponding external evidence rather than fixtures.

## M9 — Analyzer configuration UX

Status: implementation complete; full compatibility CI and live editor parity
evidence remain.

- select the analysis target and profile without editing raw settings;
- browse rules by category, inspect exact-version help, enable or exclude rules,
  and override severities;
- add an inline suppression with a required reason from a diagnostic;
- create, update, inspect, and validate adoption baselines;
- compare shared, Simulator, and device findings for the same project.

The plugin may edit `.gopdsdk-check.json` and analyzer-owned suppression
comments, but it must not embed rule semantics or maintain a second rule
catalog. Rule browsing requires an analyzer-provided versioned catalog API.
Baseline creation and update require an owned gopdsdk operation with a stable
schema and deterministic stale-entry behavior. Existing LSP configuration,
diagnostics, rule help, and safe edits remain the source of truth.

Verification: round-trip configuration fixtures, stale-document rejection,
multi-module isolation, and parity with equivalent `gopdsdk check` results.

**Tools | gopdsdk | Analyzer Configuration and Findings…** edits the selected
Go module's configuration and synchronizes its dedicated LSP client. Behavior
follows the VS Code client: default/experimental/deep profiles, catalog-derived
experimental rule IDs, rule selection/exclusion, severity overrides, exact-version
`gopdsdk/ruleHelp`, and baseline create/update/inspect/validate operations.
Editor diagnostics offer a reasoned suppression intention; policy is verified
against the executing analyzer and changed documents are rejected before editing.
Comparison invokes `check` separately for shared, Simulator, and device, disables
baseline filtering, and deduplicates the returned findings without changing rule
semantics. Baseline identities and stale entries remain analyzer-owned.

Local evidence: Kotlin compilation and focused plugin-unit fixtures for config
round trips, VS Code LSP projection parity, module isolation, stale suppression,
catalog/capability schemas, baseline results, and comparison merging. The full
three-platform/two-GoLand CI matrix and live editor checks were not run locally.
This is not SDK, Simulator, USB, or physical-device evidence.

## M10 — Device workflow and logs

Status: implementation complete; full compatibility CI and device acceptance
evidence remain external gates.

- show explicit device connection state without treating executable discovery
  as connectivity;
- build, install, and run the active application on a connected Playdate;
- expose `crashlog` and `errorlog` in read-only editor tabs;
- expose explicit Data Disk mount and safe-eject actions, keep disk mode as a
  distinct connection state, and confirm USB reconnection after eject;
- report build, connection, deployment, and launch as distinct progress stages;
- offer log inspection after a failed run only through an explicit user action
  or opt-in setting.

The plugin delegates to `gopdsdk build device`, `gopdsdk run device`,
`gopdsdk probe connection`, `gopdsdk crashlog`, `gopdsdk errorlog`, and the
versioned `gopdsdk device disk mount|unmount` operations.
Reliable connection state, staged progress, and typed failures require stable
machine-readable command results from gopdsdk. Device logs remain
user-requested evidence and must not be read silently by project opening or
background polling.

Verification: command-fixture and cancellation coverage first, followed by
separately labeled device-build, USB, and physical-device acceptance. Simulator
or sandboxed IDE tests do not establish device readiness.

Tools | gopdsdk, the Run menu, and the Playdate Device tab expose module-scoped
device commands. Operations negotiate CLI capabilities, run in cancellable
background tasks, and display CLI progress stages in the status bar. Device
operations are serialized per project. Connection state is the last explicit
operation's result, initially unchecked; it is never inferred from discovery or
background polling. Data Disk remains distinct from USB connectivity, and safe
eject succeeds only when the CLI confirms USB reconnection.

Crash/error logs open as read-only in-memory text files. Failed runs offer log
actions, but never retrieve logs automatically. Reading a log mounts Data Disk;
the user can then invoke Safely Eject Data Disk and Reconnect USB.

Verification scope: focused plugin-unit fixtures cover command arguments,
capability rejection, cancellation boundaries, typed failures, progress framing,
USB evidence, disk/eject modes, log decoding, and read-only editor files. Full
CI, live GoLand smoke checks, device-build, USB, and physical-device acceptance
must be recorded separately; no device logs were read during implementation.

## M11 — Playdate tool window

Status: implementation complete; full compatibility CI and live editor
accessibility/interaction acceptance remain external gates.

Add a Playdate tool window that summarizes the active project, selected target,
gopdsdk and Playdate SDK versions, health state, device connection, build/run
actions, logs, and diagnostic counts. The view is a projection of the M7–M10
contracts, not an independent implementation of discovery, analysis, or device
behavior.

Verification: multi-module selection, refresh, accessibility,
empty/loading/error states, action routing, and consistency with the status bar,
Problems view, and Run tool window.

The default **Playdate | Overview** tab follows the active editor's nearest Go
module and projects its analysis target, explicitly refreshed health, SDK
version, analyzer protocol version, and the same last project operation state
shown in the status bar. Module Problems counts come from GoLand's existing
Problems collector and include all IDE sources; they are not a new analysis or
a claim of complete project coverage. Existing actions provide Simulator/device
build and run, configuration, connection checks, explicit log retrieval, and
Data Disk operations. Problems and Run output remain in their native windows.

The CLI does not expose a gopdsdk release-version contract, so the overview says
it is unavailable rather than treating analyzer protocol `v1` as a release.
Health is unchecked until explicitly requested; module switches discard stale
reports, and late responses cannot overwrite a newer selection. The view's
UI-only timer never probes hardware or reads logs. Simulator actions and build
source navigation retain the selected module as their working directory.

Evidence: focused plugin-unit tests and Kotlin compilation on Windows cover
nested modules, stale refreshes, empty/loading/failure/cancellation states,
action registrations, and presentation scope. CI owns the full OS/GoLand matrix
and Plugin Verifier. Live keyboard/screen-reader interaction and consistency
with populated Problems/Run windows still require editor-integration evidence.
No SDK, Simulator, USB, or physical-device acceptance was performed locally.

## Required gopdsdk contracts

The first console-backed Simulator build/run slice and a basic `gopdsdk init`
wizard can be implemented with the current CLI. The complete roadmap requires
compatible additions to gopdsdk before the corresponding rich UI is considered
stable:

- versioned JSON results for build, run, doctor, Simulator/device probes, USB
  connection, device deployment, log retrieval, and Data Disk mount/unmount;
- structured source locations, artifact paths, typed failure categories, and
  cancellable progress events where those concepts apply;
- a versioned analyzer rule-catalog endpoint rather than a catalog copied into
  the plugin;
- deterministic baseline create/update/validate operations;
- explicit capability negotiation so older gopdsdk releases degrade to the
  supported subset instead of being parsed heuristically.

These are tooling-contract additions, not changes to the native public
`playdate` API. Each new contract must be implemented and versioned in gopdsdk
before the GoLand client depends on it.
