package com.varlanv.wrasse.model

/**
 * Declares several user-facing rule ids backed by one fused implementation (an "engine") —
 * for rules that would otherwise fight over the same region if ported as independent
 * rewriters. Users still see each id as its own entry in `wrasse.json`, with its own
 * `level`/`exclude`; [initGroup] is handed exactly the surviving ones for the current file.
 *
 * Mirrors [WUninitializedRule]'s two-phase construction: which ids are enabled and their
 * configs are decided once per compilation, [initGroup] runs fresh per file (same seam as
 * [WRuleSet.dispatchForFile]) with only the ids that are both enabled compile-wide and not
 * excluded for this specific file — an id absent from [initGroup]'s `configs` map must be
 * treated exactly as if that rule did not exist for this file.
 */
interface WUninitializedRuleGroup {
    /** Every rule id this group can back. */
    val ids: Set<String>

    /**
     * True if resolving [WContext.resolvedUsage] is required given which of [ids] are enabled
     * for this compilation (before any per-file exclude is applied — same granularity as
     * [WUninitializedRule.requiresResolution]). Default false: most groups are purely syntactic.
     */
    fun requiresResolution(enabledIds: Set<String>): Boolean = false

    /**
     * True if resolving [WResolvedUsage.qualifiedUsages] is required given which of [ids] are
     * enabled, mirroring [WUninitializedRule.requiresQualifiedUsages] at group granularity.
     * Default false.
     */
    fun requiresQualifiedUsages(enabledIds: Set<String>): Boolean = false

    /**
     * Mirrors [WUninitializedRule.canAutofix] at group granularity: true if any id backed by this
     * group ever attaches an edit to its own reports. Group-, not per-id-, granularity, because
     * every group wrasse ships today ([com.varlanv.wrasse.rules.ImportEngine]'s four ids) is
     * uniformly fixer-capable — a future group mixing a pure-report id with fixer ids would need
     * to widen this to a `Set<String>` of fixer ids instead; not needed yet, so not built.
     */
    val canAutofix: Boolean get() = false

    /** Mirrors [WUninitializedRule.options], keyed by backed id. Default: no options for any id. */
    val optionSpecs: Map<String, List<WRuleOptionSpec>> get() = emptyMap()

    /**
     * Produces a fresh, fused [WRule] instance for one file, configured with exactly the
     * enabled, non-excluded-for-this-file ids and their [WrasseRuleConfig]s. Never called with
     * an empty map — an empty surviving set means the group is skipped for this file entirely.
     */
    fun initGroup(configs: Map<String, WrasseRuleConfig>): WRule
}
