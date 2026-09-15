# Releasing

Publishing is an external, explicitly authorized operation. A successful build,
tag, or packaged artifact does not by itself authorize Marketplace publication.

## Version and channels

Every release must be built from the immutable `v<version>` tag matching
`build.gradle.kts`. Submit a signed artifact to the `eap` channel first. After
approval and live validation, publish that same version to the default channel
only when Marketplace permits promotion; otherwise increment the patch version
and repeat every gate. JetBrains Marketplace does not accept multiple artifacts
with the same version.

## Repository setup

Configure protected GitHub environments named `marketplace-eap` and
`marketplace-stable`, each requiring a reviewer. Store these secrets in the
environments:

- `CERTIFICATE_CHAIN`: PEM certificate chain for JetBrains plugin signing;
- `PRIVATE_KEY`: corresponding PEM private key;
- `PRIVATE_KEY_PASSWORD`: private-key password;
- `PUBLISH_TOKEN`: JetBrains Marketplace token, required only when publishing.

The workflow writes the certificate and private key to permission-restricted
files under `RUNNER_TEMP` and passes only their paths to Gradle. This avoids the
IntelliJ Platform Gradle Plugin limitation with raw multiline signing values.

The `release-package` environment is used for a package-only rehearsal. It needs
the three signing secrets but not a Marketplace token. Never commit or print
these values.

## Automated gates

Run the `Package GoLand plugin` workflow from the matching immutable version
tag, initially with channel `eap` and publishing disabled. It must:

1. run Plugin Verifier against GoLand 2026.1.4 and 2026.2 in isolated jobs, then
   run unit tests in the packaging job;
2. reproduce the unsigned ZIP byte-for-byte from two clean builds;
3. sign the ZIP and verify its signature;
4. generate its SHA-256, SPDX SBOM, and GitHub provenance/SBOM attestations;
5. retain the exact signed ZIP and evidence as a workflow artifact.

Pull requests and `master` builds resolve the plugin under Gradle's strict
dependency verification using the committed SHA-256 metadata. GitHub's
vulnerability-oriented Dependency Review Action may be added separately after
Dependency Graph and the required GitHub security feature are enabled for the
repository; release readiness does not claim that currently unavailable check.

Inspect the artifact and attestations. The first plugin publication must be
uploaded manually in JetBrains Marketplace. For later versions, re-run with
publication enabled only after an authorized reviewer approves the target
environment.

## Live editor gates

Use clean GoLand profiles on Windows, macOS, and Linux. Record OS version,
GoLand build, plugin commit and SHA-256, `gopdsdk` build, and workspace commit.
For GoLand 2026.1.4 and current 2026.2, verify:

1. clean installation and activation only in GoLand;
2. executable probing, diagnostics, rule identifiers and help, related
   locations, safe-fix preview/application, refresh, restart, and logs;
3. upgrade from the previous installed build, downgrade back to it, and
   uninstall without stale settings or processes;
4. the complete reliability protocol in [RELIABILITY.md](RELIABILITY.md);
5. parity against the same fixture and `gopdsdk check`/LSP result used by the VS
   Code client, with no analyzer rules implemented in either editor client.

Capture real screenshots from the tested product. Synthetic UI is not release
evidence. Store the screenshots and signed evidence record outside the source
tree until they have been reviewed for private information.

## Publication record

The release notes must record the Marketplace URL, immutable commit and tag,
ZIP SHA-256, CI run and attestations, tested GoLand and `gopdsdk` versions, each
platform result, parity fixture, and known limitations. Only then mark M6
complete. Editor tests do not establish SDK, Simulator, USB, or device evidence.
