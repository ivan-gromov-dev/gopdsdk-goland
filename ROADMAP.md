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

- stress rapid edits, reloads, branch switches, crashes, and IDE shutdown;
- measure startup, incremental latency, memory, and cancellation on a realistic
  game; keep deep analysis off by default until evidence supports it;
- manually smoke-test all three desktop platforms.

## M6 — Distribution

- icons, screenshots, changelog, privacy and support policies;
- signed CI artifacts, dependency review, and reproducible ZIPs;
- early-access, then stable JetBrains Marketplace publication;
- supported-range upgrade and downgrade checks.

Exit criterion: GoLand, VS Code, and CLI expose equivalent rule identifiers,
diagnostics, and safe fixes without analysis rules in editor clients.

## Later IDE tooling

Build, Simulator/device run, logs, deployment, and debugging are separate
features using structured gopdsdk CLI contracts and separate evidence gates.
