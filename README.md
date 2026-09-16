# gopdsdk for GoLand

Thin GoLand integration for the `gopdsdk` analyzer. The plugin starts
`gopdsdk lsp` for Go files and lets the IntelliJ Platform render Playdate
diagnostics and safe actions alongside GoLand's normal Go support.

Analysis stays in the Go executable. The Kotlin client must not copy rule
logic, severity decisions, or edit generation from gopdsdk.

## Status

This repository contains a reproducible IntelliJ Platform plugin scaffold and a
module-scoped LSP integration with a project-specific executable setting. It
prefers that explicit path and otherwise discovers `gopdsdk` deterministically
on PATH. Before starting the editor server, the plugin runs a bounded LSP
version/capability probe and reports configuration or compatibility failures
with a shortcut to Settings. Release packaging, signing, and Marketplace gates
are documented in [RELEASING.md](RELEASING.md).

## Requirements

The planned device workflow requires versioned `gopdsdk device disk
mount|unmount` results so the plugin can explicitly enter Data Disk mode, safely
eject it, and confirm the return to a connected USB state. This is a roadmap
requirement only; the GoLand client does not implement the commands yet.

- GoLand 2026.1.4 through 2026.2;
- JDK 21 for development (the Gradle wrapper can provision it automatically);
- `gopdsdk` with the `lsp` command on PATH.

An executable outside PATH can be selected under
**Settings | Tools | gopdsdk**. Applying a changed path restarts the project's
language server. Analyzer target, compatibility floors, rule/category selection,
severity overrides, baseline, changed files, and explicit deep analysis are also
project settings. Applying those settings refreshes a running server without a
restart; comma-separated fields use the same identifiers and workspace-relative
paths as `gopdsdk check`, and severity entries use `selector=severity`.

The **Tools | gopdsdk** menu also provides **Build for Simulator** and **Build
and Run in Simulator**. The same actions are available from the Run menu, main
toolbar, and Playdate tool window. They create cancellable temporary run
configurations backed by `gopdsdk build` or `gopdsdk run`; saved **Playdate
Simulator** configurations can select a package and build-only or build-and-run
behavior. Structured CLI progress appears in the status bar, output remains in
the Run console, and structured build locations become editor errors.

The menu also provides diagnostic refresh, server restart, Language Services
logs, and focused troubleshooting. Diagnostic rule links,
related locations, edit previews, cancellation, and document-version handling
use the native IntelliJ LSP client. The plugin filters code actions so only
`gopdsdk` edit-only quick fixes are offered.

The Playdate tool window includes **Project Health**, combining executable and
analyzer compatibility, module and manifest files, analyzer configuration, the
Playdate SDK, Simulator, device toolchain, and USB connection readiness. It
consumes only the versioned JSON `doctor` and `probe` contracts, exposes the raw
troubleshooting output, and keeps discovery distinct from verified readiness.
**Tools | gopdsdk | New Playdate Game…** wraps `gopdsdk init`; cancellation and
failure leave the target unopened, while success opens the generated project
and points to the first Simulator run.

## Analyzer configuration and adoption

Select a file in the desired Go module and open **Tools | gopdsdk | Analyzer
Configuration and Findings…**. The dialog stays bound to that module:

- Save the analysis target and default, experimental, or deep profile.
- Load the executing analyzer's rule catalog, filter by category, inspect
  exact-version help, enable/exclude a rule, or override its severity.
- Create, update, inspect, or validate a baseline, including stale entries.
- Compare shared, Simulator, and device findings and navigate to their sources.

Configuration changes update `.gopdsdk-check.json` and the module's LSP settings
using the same projection as the VS Code extension. The experimental profile
uses rule IDs from the analyzer catalog; deep analysis remains opt-in. Modules
without overrides inherit the project settings. Older servers remain responsible
for which configuration fields they honor.

Use **Suppress … with reason…** on an editor diagnostic, or select a comparison
finding and choose **Suppress with reason…**. The plugin requires a reason,
checks the analyzer's suppression policy, rejects stale source, and saves an
undoable `//gopdsdk:ignore` comment. Rule help is requested from the running
module language server; open a Go file first.

Comparison runs the same three CLI target checks as VS Code and bypasses
baseline filtering. Baseline creation/update uses a fresh report and delegates
identity matching and stale-entry behavior to gopdsdk. Check operations save
open documents before reading files; command cancellation is available in IDE
progress. These features require the versioned catalog, check, and baseline
contracts advertised by `gopdsdk capabilities`.

## Development

Keep local feedback lightweight:

```text
./gradlew test
```

The multi-platform `buildPlugin`, strict dependency resolution, and Plugin
Verifier matrix are CI-owned because they download and unpack multiple full
GoLand distributions. SDK/Simulator smoke tests are also external CI or release
evidence; do not run those long checks locally unless investigating a CI-only
failure.

```text
./gradlew runIde
```

The default build targets the oldest supported GoLand release. To exercise the
newest supported platform directly, run:

```text
./gradlew test -PplatformVersion=2026.2.0.1
```

`runIde` launches a sandboxed GoLand. Open a Go module that uses gopdsdk; the
native Language Services widget exposes server state and logs.

## Device workflow

Select a file in the application's Go module, then use **Tools | gopdsdk** or
the **Playdate | Device** tab to check the connection, build for device, or
build, install and run on Playdate. Build/run are also available in the Run
menu. Save operations precede execution, and the selected module remains the
command's working directory. Cancel a running operation through GoLand's
background progress UI.

The status bar shows the last explicit device operation and its CLI stages,
including compilation, connection, deployment and launch. The initial state is
unchecked; installed tools alone do not establish USB connectivity. Device
operations are serialized within a project.

**Read Crash Log** and **Read Error Log** mount Data Disk and open read-only
editor tabs. Failed runs offer these actions without reading logs automatically.
Use **Mount Data Disk** explicitly for disk access, and **Safely Eject Data Disk
and Reconnect USB** to return to USB control; completion requires the CLI's
reconnection confirmation. Cancellation or an uncertain failure requires a new
explicit connection check. Device support requires the corresponding versioned
CLI capabilities; unsupported commands ask you to update gopdsdk.

Local plugin fixtures do not prove device-build, USB or physical-device
readiness. Those acceptance checks remain external, alongside the full CI
compatibility matrix.

## Architecture

```text
GoLand Go files
    |-- bundled Go plugin  general Go language services
    `-- gopdsdk lsp       Playdate diagnostics and safe fixes
```

The plugin uses the native IntelliJ LSP client instead of embedding another
protocol implementation.

The plugin declares the GoLand product module and is intentionally unavailable
in IntelliJ IDEA or other IDEs, even when their Go plugin is installed.

Reliability coverage exercises a 603-file, three-module workload, rapid
configuration churn, repeated analyzer reloads, forced crashes, clean shutdown,
and process reaping. CI runs the deterministic plugin-unit session on Windows,
macOS, and Linux for both supported GoLand boundaries. Timings are regression
guards; live editor, SDK, Simulator, USB, and device readiness require their own
explicit evidence.
The automated and live-editor evidence procedures are documented in
[RELIABILITY.md](RELIABILITY.md).

## License

MIT. See [LICENSE](LICENSE).

For data handling, support, and vulnerability reporting, see
[PRIVACY.md](PRIVACY.md), [SUPPORT.md](SUPPORT.md), and
[SECURITY.md](SECURITY.md).
