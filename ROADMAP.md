# Development roadmap

This roadmap validates `gopdsdk lsp` through a thin GoLand client. Plugin/unit
evidence is distinct from live editor, SDK, Simulator, USB, and device evidence.

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
- [x] signed CI artifacts, dependency review, SBOM, provenance, and reproducible
  unsigned ZIP verification;
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

## Later IDE tooling

Build, Simulator/device run, logs, deployment, and debugging are separate
features using structured gopdsdk CLI contracts and separate evidence gates.
