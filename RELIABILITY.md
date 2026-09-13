# Reliability evidence

M5 separates deterministic plugin-unit coverage from live GoLand evidence. A
passing unit session does not establish editor, SDK, Simulator, USB, or physical
device readiness.

## Automated session

Run:

```text
./gradlew test --tests dev.gopdsdk.goland.ReliabilityTest
```

The test models a 603-file, three-module game, applies 40 rapid analyzer-setting
changes, and runs 60 compatibility/startup cycles with six forced analyzer
crashes. It fails when a process survives, a clean server misses shutdown, deep
analysis becomes enabled by default, or the bounded startup workload exceeds
its regression guard. The JSON line in the test output is plugin-unit evidence.

CI runs the session on Windows, macOS, and Linux against both supported GoLand
boundaries.

## Live editor smoke

For each supported desktop platform, record the operating-system version,
GoLand build, plugin commit, gopdsdk build, and workspace commit. Use a game with
roughly 600 Go files across at least three modules, with deep analysis disabled.

1. Open the game and record time to the first gopdsdk diagnostic and steady-state
   GoLand RSS.
2. Make 40 quick edits in one Go file. Confirm current-version diagnostics,
   inspect Language Services logs for cancellation, and record the longest
   incremental-diagnostic latency.
3. Reload the project six times, switch branches twice, and confirm diagnostics
   clear and return without duplicate clients or notifications.
4. Terminate the analyzer process. Confirm one recovery, current diagnostics,
   and no abandoned analyzer process.
5. Close GoLand. Confirm the analyzer exits and record final RSS growth.

Attach the observations as editor-integration evidence. Fail the smoke test for
stale diagnostics, an unrecovered crash, leaked processes, repeated error
notifications, or unbounded latency or memory growth relative to the recorded
baseline.
