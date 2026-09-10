# Development roadmap

This roadmap validates `gopdsdk lsp` through a thin GoLand client. Plugin/unit
evidence is distinct from live editor, SDK, Simulator, USB, and device evidence.

## M0 — Scaffold

Status: complete, except for generating the Gradle wrapper and build verification.

- Kotlin/JVM and IntelliJ Platform Gradle Plugin 2.x;
- GoLand and bundled Go-plugin dependencies;
- native project-wide LSP integration for Go files;
- plugin metadata and verification configuration.

## M1 — Reproducible bootstrap

- check in the Gradle 9 wrapper and dependency verification data;
- pin the minimum GoLand platform and test the newest compatible release;
- add Windows, macOS, and Linux CI;
- run Plugin Verifier at every compatibility boundary.

## M2 — Executable contract

- project settings and deterministic executable discovery;
- bounded version/capability probe with actionable notifications;
- restart after settings changes, clean shutdown, and redacted logs;
- test missing, old, incompatible, crashing, and cleanly exiting servers.

## M3 — Analyzer configuration

- target, SDK floor, rules/categories, severity, baseline, changed-file mode,
  and explicit deep-analysis opt-in;
- project persistence and multi-module behavior;
- configuration refresh without restart when supported.

Verification: parity with equivalent `gopdsdk check` executions.

## M4 — Diagnostic UX

- related locations, stable rule identifiers, and rule help;
- preview and apply only analyzer-provided safe fixes;
- restart, refresh, logs, and troubleshooting actions;
- graceful coexistence with the bundled Go plugin.

Verification: platform and sandboxed UI tests for edits, saves, stale versions,
clearing, cancellation, fixes, and multi-module projects.

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
