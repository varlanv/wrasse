package com.varlanv.wrasse.rules

/**
 * Kotlin/JVM's compiler-default-imported packages: a bare name in one of these already resolves
 * with no import directive at all, so `no-unnecessary-fqn` must never add a new `import` for a
 * candidate whose package is here — that would be pure noise on top of what the compiler already
 * brings into scope for free.
 *
 * Derived from `kotlin-compiler-embeddable`'s own `DefaultImportsProvider` and
 * `JvmDefaultImportsProvider` classes: the `kotlin.*` family plus the JVM-platform additions
 * `kotlin.jvm.*` and `java.lang.*`.
 */
object DefaultImportPackages {

    val ALL: Set<String> = setOf(
        "kotlin",
        "kotlin.annotation",
        "kotlin.collections",
        "kotlin.comparisons",
        "kotlin.io",
        "kotlin.ranges",
        "kotlin.sequences",
        "kotlin.text",
        "kotlin.jvm",
        "java.lang",
    )
}
