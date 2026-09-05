package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallSite
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher

/**
 * Pure decision core of [ForbiddenCallsRule]. A callee's canonical name is `pkg.Class.member` for
 * a member, `pkg.member` for a top-level callable, and the class's own `pkg.Class` for a
 * constructor. A pattern matches a canonical name exactly, or by prefix when it ends in `*`.
 */
object ForbiddenCallsDecision {
    class ForbiddenCall(val pattern: String, val allowedIn: List<PathMatcher>) {
        fun matches(canonicalName: String): Boolean = if (pattern.endsWith("*")) {
            canonicalName.regionMatches(0, pattern, 0, pattern.length - 1)
        } else {
            canonicalName == pattern
        }

        fun allowedIn(configRelativeFilePath: Path): Boolean = allowedIn.any { it.matches(configRelativeFilePath) }
    }

    fun compile(calls: Map<String, List<String>>): List<ForbiddenCall> {
        val fileSystem = FileSystems.getDefault()
        val result = ArrayList<ForbiddenCall>(calls.size)
        for ((pattern, globs) in calls) {
            result.add(ForbiddenCall(pattern, globs.map { fileSystem.getPathMatcher("glob:$it") }))
        }
        return result
    }

    fun canonicalName(site: WCallSite): String {
        val classFqName = site.calleeClassFqName
        return when {
            classFqName == null -> site.calleePackageFqName + "." + site.calleeName
            site.isConstructor -> classFqName
            else -> classFqName + "." + site.calleeName
        }
    }

    fun message(canonicalName: String): String = "Call to '$canonicalName' is forbidden"
}
