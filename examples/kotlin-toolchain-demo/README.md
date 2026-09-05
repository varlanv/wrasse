# wrasse with the JetBrains Kotlin Toolchain (ex-Amper)

A two-module toolchain project: `app` compiles with the wrasse compiler plugin, `wrasse-apply` is
a local toolchain plugin that turns the patch wrasse emitted into a `./kotlin do wrasseApply`
command. No Gradle anywhere.

```
./kotlin build            # compile; wrasse reports every violation as a compiler warning
./kotlin do wrasseApply   # write the fixes wrasse recorded under build/wrasse
./kotlin build            # clean, apart from the rules that have no autofix
./kotlin run
```

`./format.sh` does the same as the first two commands but asks the plugin to stay quiet about
the findings it is about to fix: it writes `build/wrasse/app/format-request` (a timestamp and
`formatting=true`) before the build, which the plugin consumes on start. The toolchain has no
hook to run a task only ahead of a format build, so this lives in a script.

`app/src/Main.kt` ships unformatted on purpose (2-space indent, semicolons, wildcard and unused
imports, unsorted imports, positional arguments, an unnecessary FQN, a one-line if/else).

## How it is wired

`app/module.yaml` declares the plugin the way the toolchain declares any third-party compiler
plugin; the options are wrasse's `CommandLineProcessor` options:

```yaml
settings:
  kotlin:
    compilerPlugins:
      - id: com.varlanv.wrasse
        dependency: com.varlanv.wrasse:compiler-plugin:0.0.1-SNAPSHOT
        options:
          warnOnly: true
          fixOutputDir: build/wrasse/app
```

`wrasse-apply/` is a `jvm/amper-plugin` module depending on `wrasse-lang` only; its single
`@TaskAction` calls `WPatchApplier.apply` on `${project.rootDir}/build/wrasse` and `plugin.yaml`
exposes it as the `wrasseApply` command. `fixOutputDir` is resolved by kotlinc against the working
directory, which for the toolchain is the project root, hence the `build/wrasse/<module>` layout.

Configuration is the ordinary `wrasse.json` at the project root, found by walking up from the
source root exactly as under Gradle.

## Requirements

- The wrasse artifacts in `~/.m2` (`./gradlew publishToMavenLocal` in this repo); the module lists
  `mavenLocal` as a repository.
- Any Kotlin the toolchain can run and wrasse supports (2.1 to 2.4); verified with the toolchain
  default and with `version: 2.2.21` (which needs `settings.jvm.jdk.version: 21`, the 2.2 compiler
  rejects JDK 25).

## Observed while writing this

- The toolchain renders the diagnostics with source excerpts on Kotlin 2.4 and as plain `WARN`
  lines on 2.2.
