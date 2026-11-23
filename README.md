# Wrasse

A Kotlin linter and formatter that runs inside the Kotlin compiler.
Reads the syntax tree `kotlinc` has already built, so linting adds no extra parse pass to your build.

Early stage: a growing set of lint rules, autofixes and an opinionated formatter. Supports Kotlin 2.4.21; other versions get a warning from the Gradle plugin.

## Usage

```kotlin
plugins {
    kotlin("jvm") version "2.4.21"
    id("com.varlanv.wrasse") version "0.1.0"
}
```

Works with the Kotlin JVM, Multiplatform and Android plugins. Nothing else to install: the compiler plugin ships inside
the Gradle plugin.

Create a `wrasse.json` in the project (or any parent directory). The build fails at configuration time if none is found.

```json
{
    "format": {
        "enabled": true
    },
    "rules": {
        "no-semicolons": {
            "level": "error"
        },
        "no-wildcard-imports": {
            "level": "error"
        },
        "no-unused-imports": {
            "level": "error"
        },
        "import-ordering": {
            "level": "error"
        },
        "trailing-newline": {
            "level": "warn"
        }
    }
}
```

Rule levels are `off`, `warn` and `error`. A rule that is not listed is off. A config can inherit another with
`"extends"`. `wrasse-schema.json` in this repository provides editor autocompletion.

## Tasks

| Task           | What it does                                                  |
|----------------|---------------------------------------------------------------|
| `wrasseLint`   | Compiles and reports every finding; fails on error-level ones |
| `wrasseFormat` | Applies autofixes and formatting to the sources               |
| `wrasseApply`  | Applies the patches recorded by the last compile              |

Findings are also reported by a normal `compileKotlin`, as warnings by default.

## Configuration

```kotlin
wrasse {
    enabled.set(true)
    warnOnly.set(true)
    configFile.set(file("config/wrasse.json"))
}
```

- `enabled` — run the compiler plugin in every Kotlin compile (default `true`).
- `warnOnly` — keep error-level findings from failing the compile itself, leaving `wrasseLint` as the gate (default
  `true`).
- `configFile` — the config to use instead of the one found by walking up from the project directory.

## License

[MIT](LICENSE)
