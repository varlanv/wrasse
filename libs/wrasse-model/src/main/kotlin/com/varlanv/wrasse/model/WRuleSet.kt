package com.varlanv.wrasse.model

/**
 * The set of rules active for a compilation, held as `(WUninitializedRule, WrasseRuleConfig)`
 * pairs so nothing is instantiated yet. Which rule IDs are enabled and their configs are
 * decided once per compilation (by the config layer); [dispatchForFile] is the per-file seam
 * that turns that decision into a fresh [StreamDispatch].
 *
 * Each call to [dispatchForFile] calls [WUninitializedRule.initRule] again for every
 * surviving rule, so every file gets its own rule instances — no shared per-file mutable
 * state, no reset step needed. A rule whose [isExcluded] check matches the current file is
 * skipped entirely: it is never instantiated for that file, not merely filtered afterward.
 *
 * Rebuilding [StreamDispatch]'s ordinal-indexed arrays costs `O(WNodeType.SIZE)`, fixed
 * regardless of how many rules are active, and negligible next to walking the file itself;
 * that cost does not scale with rule count, so this stays a plain per-file rebuild rather
 * than a cached dispatch shape until profiling says otherwise. The `dispatchForFile` contract
 * would not need to change if that optimization ever lands.
 */
class WRuleSet(
    private val activeRules: List<Pair<WUninitializedRule, WrasseRuleConfig>>,
) {
    /** True if any active rule opts into [WUninitializedRule.requiresResolution]. Computed once, not per file. */
    val requiresResolution: Boolean = activeRules.any { (uninitialized, _) -> uninitialized.requiresResolution }

    fun dispatchForFile(isExcluded: (WrasseRuleConfig) -> Boolean): StreamDispatch {
        val rules = ArrayList<WRule>(activeRules.size)
        for ((uninitialized, config) in activeRules) {
            if (isExcluded(config)) continue
            rules.add(uninitialized.initRule(config))
        }
        return StreamDispatch(rules)
    }
}
