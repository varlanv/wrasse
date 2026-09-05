package com.varlanv.wrasse.model

/**
 * One resolved call written with call syntax (`f(...)`, `a.f(...)`, `a?.f(...)`), as FIR mapped
 * its arguments onto the callee's parameters. [callStartOffset]/[callEndOffset] span the whole
 * call expression, receiver and trailing lambda included — [callEndOffset] is also where the
 * call's own `CALL_EXPRESSION` node ends. [arguments] holds every argument FIR mapped, in source
 * order — a trailing lambda and each element of a vararg parameter included, empty for a call
 * without arguments. [parameterCount] is how many value parameters the callee declares (a vararg
 * counts once). Collected only when a rule asks for it ([WUninitializedRule.requiresCallSites]).
 */
class WCallSite(
    val callStartOffset: Int,
    val callEndOffset: Int,
    /** Package of the callee; for a member, its containing class's package. */
    val calleePackageFqName: String,
    /** Fully-qualified name of the callee's containing class, or null for a top-level callable. */
    val calleeClassFqName: String?,
    val calleeName: String,
    /** False when the callee's parameter names are not usable at a call site (a Java method, for one). */
    val hasStableParameterNames: Boolean,
    val arguments: List<WCallArgument>,
    /** True for a constructor call; [calleeName] is then the class's own short name. */
    val isConstructor: Boolean = false,
    val parameterCount: Int = 0,
)

/**
 * [startOffset]/[endOffset] span the argument's value expression (never its `name = ` prefix —
 * FIR unwraps named arguments, so whether one was written named is a syntactic question left to
 * the rule). [parameterName] is the declared name of the parameter FIR mapped it to; [isVararg]
 * marks one element of a vararg parameter.
 */
class WCallArgument(
    val startOffset: Int,
    val endOffset: Int,
    val parameterName: String,
    val isVararg: Boolean,
)
