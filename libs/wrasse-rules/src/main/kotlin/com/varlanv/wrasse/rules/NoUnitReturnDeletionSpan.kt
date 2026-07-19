package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Deletion-span policy for a redundant `: Unit` function return type, compiler-free so it is
 * unit-testable without a kotlinc dependency.
 *
 * Only called for the shape [NoUnitReturnRule] has already proven safe: nothing but whitespace
 * sits between the colon and the `Unit` type reference. The colon, that whitespace, and `Unit`
 * itself collapse to nothing in one contiguous deletion (`colonStart` to `typeReferenceEnd`);
 * whatever follows `Unit` (the space before `{`, a comment, a newline) is never touched, so
 * `fun foo(): Unit {}` collapses to `fun foo() {}` and `fun foo(): Unit\n{\n}` collapses to
 * `fun foo()\n{\n}` with no extra whitespace introduced or removed on either side.
 */
object NoUnitReturnDeletionSpan {

    fun compute(colonStart: Int, typeReferenceEnd: Int): WEdit = WEdit(colonStart, typeReferenceEnd, "")
}
