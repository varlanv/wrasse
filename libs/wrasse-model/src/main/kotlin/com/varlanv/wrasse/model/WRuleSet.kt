package com.varlanv.wrasse.model

/**
 * The set of rules active for a compilation, held as `(WUninitializedRule, WrasseRuleConfig)`
 * pairs plus `(WUninitializedRuleGroup, Map<id, WrasseRuleConfig>)` pairs for fused
 * multi-id engines (see [WUninitializedRuleGroup]) — nothing is instantiated yet. Which rule
 * IDs are enabled and their configs are decided once per compilation (by the config layer);
 * [dispatchForFile] is the per-file seam that turns that decision into a fresh [StreamDispatch].
 *
 * Each call to [dispatchForFile] calls [WUninitializedRule.initRule] (or
 * [WUninitializedRuleGroup.initGroup]) again for every surviving rule/group, so every file gets
 * its own rule instances — no shared per-file mutable state, no reset step needed. A rule whose
 * [isExcluded] check matches the current file is skipped entirely: it is never instantiated for
 * that file, not merely filtered afterward. For a group, each id's config is filtered by
 * [isExcluded] independently; the group is skipped entirely only if every one of its ids is
 * excluded for this file, otherwise [WUninitializedRuleGroup.initGroup] receives exactly the
 * surviving subset.
 *
 * Rebuilding [StreamDispatch]'s ordinal-indexed arrays costs `O(WNodeType.SIZE)`, fixed
 * regardless of how many rules are active, and negligible next to walking the file itself;
 * that cost does not scale with rule count, so this stays a plain per-file rebuild rather
 * than a cached dispatch shape until profiling says otherwise. The `dispatchForFile` contract
 * would not need to change if that optimization ever lands.
 */
class WRuleSet(
    private val activeRules: List<Pair<WUninitializedRule, WrasseRuleConfig>>,
    private val activeGroups: List<Pair<WUninitializedRuleGroup, Map<String, WrasseRuleConfig>>> = emptyList(),
) {
    /**
     * True if any active rule or group opts into requiring [WContext.resolvedUsage]. Computed
     * once, not per file, from the compile-wide enabled set (before per-file exclude).
     */
    val requiresResolution: Boolean =
        activeRules.any { (uninitialized, _) -> uninitialized.requiresResolution } ||
            activeGroups.any { (group, configs) -> group.requiresResolution(configs.keys) }

    /**
     * True if any active rule or group opts into requiring [WResolvedUsage.qualifiedUsages].
     * Computed once, not per file, mirroring [requiresResolution].
     */
    val requiresQualifiedUsages: Boolean =
        activeRules.any { (uninitialized, _) -> uninitialized.requiresQualifiedUsages } ||
            activeGroups.any { (group, configs) -> group.requiresQualifiedUsages(configs.keys) }

    /**
     * [alwaysOn] carries framework-owned rules that ride the same single walk as every
     * user-configured rule but are never part of the user's rule set (no id in `wrasse.json`,
     * never excluded) — e.g. the `@Suppress` region collector, which every rule's reporting
     * depends on regardless of which rules are active. Appended after the user's own rules so it
     * has no effect on their dispatch ordinal-array construction beyond its own entry.
     */
    fun dispatchForFile(alwaysOn: List<WRule> = emptyList(), isExcluded: (WrasseRuleConfig) -> Boolean): StreamDispatch {
        val rules = ArrayList<WRule>(activeRules.size + activeGroups.size + alwaysOn.size)
        for ((uninitialized, config) in activeRules) {
            if (isExcluded(config)) continue
            rules.add(uninitialized.initRule(config))
        }
        for ((group, configs) in activeGroups) {
            val surviving = configs.filterValues { config -> !isExcluded(config) }
            if (surviving.isEmpty()) continue
            rules.add(group.initGroup(surviving))
        }
        rules.addAll(alwaysOn)
        return StreamDispatch(rules)
    }
}
