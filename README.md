# insta-kotlin in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Finsta--kotlin-blue.svg)](https://github.com/KotlinMania/insta-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/insta-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/insta-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/insta-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/insta-kotlin/actions)

This is a Kotlin Multiplatform line-by-line transliteration port of [`mitsuhiko/insta`](https://github.com/mitsuhiko/insta).

**Original Project:** This port is based on [`mitsuhiko/insta`](https://github.com/mitsuhiko/insta). All design credit and project intent belong to the upstream authors; this repository is a faithful port to Kotlin Multiplatform with no behavioural changes intended.

### Porting status

This is an **in-progress port**. The goal is feature parity with the upstream Rust crate while providing a native Kotlin Multiplatform API. Every Kotlin file carries a `// port-lint: source <path>` header naming its upstream Rust counterpart so the AST-distance tool can track provenance.

---

## Upstream README — `mitsuhiko/insta`

> The text below is reproduced and lightly edited from [`https://github.com/mitsuhiko/insta`](https://github.com/mitsuhiko/insta). It is the upstream project's own description and remains under the upstream authors' authorship; links have been rewritten to absolute upstream URLs so they continue to resolve from this repository.

<div align="center">
 <img src="https://github.com/mitsuhiko/insta/blob/master/assets/logo.png?raw=true" width="250" height="250">
 <p><strong>insta: a snapshot testing library for Rust</strong></p>
</div>

[![Crates.io](https://img.shields.io/crates/d/insta.svg)](https://crates.io/crates/insta)
[![License](https://img.shields.io/github/license/mitsuhiko/insta)](https://github.com/mitsuhiko/insta/blob/master/LICENSE)
[![Documentation](https://docs.rs/insta/badge.svg)](https://docs.rs/insta)
[![VSCode Extension](https://img.shields.io/visual-studio-marketplace/v/mitsuhiko.insta?label=vscode%20extension)](https://marketplace.visualstudio.com/items?itemName=mitsuhiko.insta)

## Introduction

Snapshots tests (also sometimes called approval tests) are tests that
assert values against a reference value (the snapshot). This is similar
to how `assert_eq!` lets you compare a value against a reference value but
unlike simple string assertions, snapshot tests let you test against complex
values and come with comprehensive tools to review changes.

Snapshot tests are particularly useful if your reference values are very
large or change often.

## Example

```rust
#[test]
fn test_hello_world() {
    insta::assert_debug_snapshot!(vec![1, 2, 3]);
}
```

Curious? There is a screencast that shows the entire workflow: [watch the insta
introduction screencast](https://www.youtube.com/watch?v=rCHrMqE4JOY&feature=youtu.be).
Or if you're not into videos, read the [5 minute introduction](https://insta.rs/docs/quickstart/).

Insta also supports inline snapshots which are stored right in your source file
instead of separate files. This is accomplished by the companion
[cargo-insta](https://github.com/mitsuhiko/insta/tree/master/cargo-insta) tool.

## Editor Support

For looking at `.snap` files there is a [vscode extension](https://github.com/mitsuhiko/insta/tree/master/vscode-insta)
which can syntax highlight snapshot files, review snapshots and more. It can be installed from the
marketplace: [view on marketplace](https://marketplace.visualstudio.com/items?itemName=mitsuhiko.insta).

![jump to definition](https://raw.githubusercontent.com/mitsuhiko/insta/master/vscode-insta/images/jump-to-definition.gif)

## Diffing

Insta uses [`similar`](https://github.com/mitsuhiko/similar) for all its diffing
operations. You can use it independently of insta. You can use the
[`similar-asserts`](https://github.com/mitsuhiko/similar-asserts) crate to get
inline diffs for the standard `assert_eq!` macro to achieve insta like diffs
for regular comparisons:

```rust
use similar_asserts::assert_eq;

fn main() {
    let reference = vec![1, 2, 3, 4];
    assert_eq!(reference, (0..4).collect::<Vec<_>>());
}
```

## Sponsor

If you like the project and find it useful you can [become a
sponsor](https://github.com/sponsors/mitsuhiko).

## License and Links

- [Project Website](https://insta.rs/)
- [Documentation](https://docs.rs/insta/)
- [Issue Tracker](https://github.com/mitsuhiko/insta/issues)
- License: [Apache-2.0](https://github.com/mitsuhiko/insta/blob/master/LICENSE)

---

## About this Kotlin port

### Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:insta-kotlin:0.1.0-SNAPSHOT")
}
```

### Building

```bash
./gradlew build
./gradlew test
```

### Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

### Porting guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for translator discipline, port-lint header convention, and Rust → Kotlin idiom mapping.

### License

This Kotlin port is distributed under the same Apache-2.0 license as the upstream [`mitsuhiko/insta`](https://github.com/mitsuhiko/insta). See [LICENSE](LICENSE) (and any sibling `LICENSE-*` / `NOTICE` files mirrored from upstream) for the full text.

Original work copyrighted by the insta authors.  
Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

### Acknowledgments

Thanks to the [`mitsuhiko/insta`](https://github.com/mitsuhiko/insta) maintainers and contributors for the original Rust implementation. This port reproduces their work in Kotlin Multiplatform; bug reports about upstream design or behavior should go to the upstream repository.
