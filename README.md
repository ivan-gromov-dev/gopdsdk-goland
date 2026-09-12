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
with a shortcut to Settings. Marketplace packaging is tracked in
[ROADMAP.md](ROADMAP.md).

## Requirements

- GoLand 2026.1.4 through 2026.2;
- JDK 21 for development (the Gradle wrapper can provision it automatically);
- `gopdsdk` with the `lsp` command on PATH.

An executable outside PATH can be selected under
**Settings | Tools | gopdsdk**. Applying a changed path restarts the project's
language server.

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

## License

MIT. See [LICENSE](LICENSE).
