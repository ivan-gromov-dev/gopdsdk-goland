# gopdsdk for GoLand

Thin GoLand integration for the `gopdsdk` analyzer. The plugin starts
`gopdsdk lsp` for Go files and lets the IntelliJ Platform render Playdate
diagnostics and safe actions alongside GoLand's normal Go support.

Analysis stays in the Go executable. The Kotlin client must not copy rule
logic, severity decisions, or edit generation from gopdsdk.

## Status

This repository contains a reproducible IntelliJ Platform plugin scaffold and a
project-wide LSP integration with a project-specific executable setting. It
prefers that explicit path and otherwise discovers `gopdsdk` deterministically
on PATH. Before starting the editor server, the plugin runs a bounded LSP
version/capability probe and reports configuration or compatibility failures
with a shortcut to Settings. Release packaging, signing, and Marketplace gates
are documented in [RELEASING.md](RELEASING.md).

## Requirements

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

The **Tools | gopdsdk** menu provides diagnostic refresh, server restart,
Language Services logs, and focused troubleshooting. Diagnostic rule links,
related locations, edit previews, cancellation, and document-version handling
use the native IntelliJ LSP client. The plugin filters code actions so only
`gopdsdk` edit-only quick fixes are offered.

## Development

```text
./gradlew runIde
./gradlew test
./gradlew buildPlugin
./gradlew verifyPlugin
```

The default build targets the oldest supported GoLand release. To exercise the
newest supported platform directly, run:

```text
./gradlew test -PplatformVersion=2026.2.0.1
```

`runIde` launches a sandboxed GoLand. Open a Go module that uses gopdsdk; the
native Language Services widget exposes server state and logs.

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
