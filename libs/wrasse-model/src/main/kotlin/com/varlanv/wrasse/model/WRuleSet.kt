package com.varlanv.wrasse.model

/**
 * The set of rules active for a compilation: `(WUninitializedRule, WrasseRuleConfig)` pairs plus
 * `(WUninitializedRuleGroup, Map<id, WrasseRuleConfig>)` pairs for fused multi-id engines (see
 * [WUninitializedRuleGroup]) — nothing is instantiated yet. [dispatchForFile] is the per-file seam
 * that turns the compile-wide rule/config decision into a fresh [StreamDispatch].
 *
 * Each call to [dispatchForFile] re-initializes every surviving rule/group, so every file gets its
 * own rule instances with no shared per-file mutable state. A rule whose [isExcluded] check
 * matches the current file is never instantiated for that file. For a group, each id's config is
 * filtered by [isExcluded] independently; the group is skipped entirely only if every id is
 * excluded, otherwise [WUninitializedRuleGroup.initGroup] receives exactly the surviving subset.
 */
class WRuleSet(
    private val activeRules: List<Pair<WUninitializedRule, WrasseRuleConfig>>,
    private val activeGroups: List<Pair<WUninitializedRuleGroup, Map<String, WrasseRuleConfig>>> = emptyList(),
) {
    /**
     * True if any active rule or group opts into requiring [WContext.resolvedUsage]. Computed
     * once from the compile-wide enabled set, before per-file exclusion.
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
     * [alwaysOn] carries framework-owned rules that ride the same walk as every user-configured
     * rule but are never part of the user's rule set (no id in `wrasse.json`, never excluded) —
     * e.g. the `@Suppress` region collector. Appended after the user's own rules.
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
