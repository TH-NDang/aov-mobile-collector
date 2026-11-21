# Kotlin code style

Use clear names, focused functions, and one responsibility per helper. Keep verification,
permission checks, cancellation, locking, and durable writes in their original order when
refactoring safety-sensitive file operations.

## Formatting

The project uses ktlint **1.5.0**, the Android Studio style, four-space indentation,
a 140-column limit, and an explicit blank line before declarations. `.editorconfig`
is the shared editor configuration. `tools/ktlint` downloads the pinned official
Maven Central CLI artifact and verifies its SHA-256 before running it.

Requires Java 17 or newer, Bash, curl, and sha256sum.

- Check: `./tools/ktlint`
- Format: `./tools/ktlint --format`

CI runs the same check before the existing Android build, Android lint, unit tests,
and emulator smoke tests. Formatting does not replace those checks.

## Existing lint debt

`config/ktlint-baseline.xml` records the remaining existing wildcard imports,
legacy property names, and overlong lines after the mechanical formatting pass.
These rules are still enabled. The baseline lists individual findings by file,
line, column, and rule; it is not a blanket rule exclusion. Do not regenerate it
to make new violations pass. Resolve or remove individual entries as the relevant
code is deliberately improved. Preserve literal UI text, OCR fixtures, persisted
keys, and public names unless a separate reviewed change requires otherwise.

Keep broad formatting-only commits separate from behavior or responsibility changes,
so reviewers can compare refactors against an already formatted parent commit.
