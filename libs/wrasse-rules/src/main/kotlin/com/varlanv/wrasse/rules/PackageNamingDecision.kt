package com.varlanv.wrasse.rules

/**
 * A package name must contain no underscore, and each dot-separated segment must start with a
 * lowercase letter followed by letters or digits — permissive enough to accept digits and mixed
 * case after a segment's first letter. A corporate reverse-domain-prefix policy is a distinct,
 * project-specific check, out of scope here.
 */
object PackageNamingDecision {
    const val UNDERSCORE_MESSAGE = "Package name must not contain underscore"
    const val MESSAGE = "Package name contains a disallowed character"

    fun decide(packageFqName: String): String? {
        if (packageFqName.isEmpty()) return null
        if (packageFqName.contains('_')) return UNDERSCORE_MESSAGE
        val segments = packageFqName.split('.')
        return if (segments.all { IdentifierCasing.isLowerDottedSegment(it) }) null else MESSAGE
    }
}
