# gopdsdk for GoLand

Thin GoLand integration for the `gopdsdk` analyzer. The plugin starts
`gopdsdk lsp` for Go files and lets the IntelliJ Platform render Playdate
diagnostics and safe actions alongside GoLand's normal Go support.

Analysis stays in the Go executable. The Kotlin client must not copy rule
logic, severity decisions, or edit generation from gopdsdk.

## Status

This repository contains the initial IntelliJ Platform plugin scaffold and a
minimal project-wide LSP integration. It currently expects `gopdsdk` on PATH.
Settings, compatibility checks, tests, and Marketplace packaging are tracked in
[ROADMAP.md](ROADMAP.md).

## Requirements

- GoLand 2026.1.4 through 2026.2;
- JDK 21 and Gradle 9 for development;
- `gopdsdk` with the `lsp` command on PATH.

## Development

```text
gradle runIde
gradle test
gradle buildPlugin
gradle verifyPlugin
```

`runIde` launches a sandboxed GoLand. Open a Go module that uses gopdsdk; the
native Language Services widget exposes server state and logs. Generating and
checking in a Gradle wrapper is the first bootstrap roadmap task.

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
