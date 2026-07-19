package com.varlanv.wrasse.rules

/**
 * Kotlin/JVM's compiler-default-imported packages: a bare name in one of these already resolves
 * with no import directive at all, so `no-unnecessary-fqn` (D.3, design.md §8) must never add one
 * for a candidate whose package is here (the `kotlin.Unit` dogfood case, commit cf6d939) — doing
 * so would be pure noise on top of what the compiler already brings into scope for free.
 *
 * javap-confirmed against `kotlin-compiler-embeddable:2.3.21`: the base list is every
 * `ImportPath` literal constructed in `org.jetbrains.kotlin.resolve.DefaultImportsProvider`'s own
 * constructor (`kotlin.*`, `kotlin.annotation.*`, `kotlin.collections.*`, `kotlin.comparisons.*`,
 * `kotlin.io.*`, `kotlin.ranges.*`, `kotlin.sequences.*`, `kotlin.text.*`); the two JVM-platform
 * additions (`kotlin.jvm.*`, `java.lang.*`) are the string constants loaded in
 * `org.jetbrains.kotlin.resolve.jvm.platform.JvmDefaultImportsProvider`'s
 * `platformSpecificDefaultImports` lambda, read directly off the class files (`javap -c
 * -constants`), not assumed from documentation.
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
