package com.varlanv.wrasse.rules

/**
 * When a file has exactly one non-private top-level class, interface, or object, the file's own
 * name (without `.kt`) must equal that declaration's name. Otherwise the file name must be
 * PascalCase — a single-typealias/object file, or a file with several top-level declarations, both
 * fold into this plain PascalCase fallback rather than a more particular per-shape check.
 */
object FileNamingDecision {
    fun decide(fileStem: String, singleNonPrivateClassLikeName: String?): String? {
        if (singleNonPrivateClassLikeName != null) {
            return if (fileStem == singleNonPrivateClassLikeName) {
                null
            } else {
                "File '$fileStem.kt' contains a single top-level class or object and should be named " +
                    "'$singleNonPrivateClassLikeName.kt'"
            }
        }
        return if (IdentifierCasing.isPascalCase(fileStem)) null else "File name '$fileStem.kt' should be PascalCase"
    }
}
