package com.varlanv.wrasse.model

/**
 * File-level facade over what a file's FIR resolution already knows: every classifier and
 * callable it resolved a reference to, and whether resolution hit an error anywhere.
 *
 * Built once per file by the compiler-plugin host (Phase B.3, [WContext.resolvedUsage]) and
 * carries zero kotlinc types, so it is usable from [wrasse-rules] without a kotlinc dependency.
 */
class WResolvedUsage(
    /** Fully-qualified names (e.g. "kotlin.collections.List") of every classifier the file references, including type arguments, annotation types, and qualifier references. */
    val classifiers: Set<String>,
    /** Every resolved callable (function, property, constructor) the file references. */
    val callables: Set<WCallableUsage>,
    /** True if the file contains any unresolved reference or error type. Consumers must bail entirely on such files. */
    val hasResolutionErrors: Boolean,
    /** The file's own import directives as FIR resolved them, in source order, one entry per directive (duplicates included). */
    val resolvedImports: List<WResolvedImport>,
)

/**
 * A single resolved callable reference: a top-level callable (incl. extensions) when
 * [classFqName] is null, otherwise a member of the class named by [classFqName].
 */
class WCallableUsage(
    /** Package containing the callable (for members, the package of the containing class). */
    val packageFqName: String,
    /** Fully-qualified name of the containing class, or null for a top-level callable. */
    val classFqName: String?,
    /** Simple name of the callable. */
    val name: String,
    /**
     * True for a Java static member or a Kotlin enum entry — the only callable member kinds
     * explicit `import Owner.member` accepts from a non-singleton owner (mirrors the compiler's
     * own `getImportStatusOfCallableMembers` non-singleton branch, which checks exactly this
     * flag). False for an ordinary instance member and for a member of a singleton owner
     * (Kotlin `object`/companion), where this flag plays no role in legality. Not part of
     * [equals]/[hashCode] — informational only, always identical for a given declaration.
     */
    val isStatic: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        other is WCallableUsage &&
            packageFqName == other.packageFqName &&
            classFqName == other.classFqName &&
            name == other.name

    override fun hashCode(): Int {
        var result = packageFqName.hashCode()
        result = 31 * result + (classFqName?.hashCode() ?: 0)
        result = 31 * result + name.hashCode()
        return result
    }
}

/**
 * One `FirImport`/`FirResolvedImport` as the file's own imports (`FirFile.imports`), collected
 * in source order — including duplicates, one entry per textual directive; carries no alias
 * (rule code already tracks that syntactically off the leaf stream).
 */
class WResolvedImport(
    /** The imported FQN: for a star import, the package/class being starred (no trailing `.*`); for an explicit import, the full imported name. */
    val fqn: String,
    /** True for `import fqn.*`. */
    val isStarImport: Boolean,
    /**
     * Non-null iff the import's parent resolves to a class/object: for a star import, this is
     * the FIR compiler's own answer to "is [fqn] itself a class/object" (empirically confirmed
     * always equal to [fqn] when non-null, from `FirImportResolveTransformer`'s use of the star's
     * own FQN, unlike an explicit import's parent which is `fqn`'s parent); for an explicit
     * import, non-null means [fqn]'s parent is a class/object (a member import — enum entry,
     * object/companion member, Java static, or nested class). Null means the parent is a package.
     */
    val resolvedParentClassFqName: String?,
    /** False when this import never became a `FirResolvedImport` (best-effort; a syntactically valid, non-root import is wrapped as `FirResolvedImport` regardless of whether it actually resolves — see design.md §8). Consumers must bail on false. */
    val resolved: Boolean,
)
