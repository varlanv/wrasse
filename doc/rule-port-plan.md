# Wrasse — Rule Port Plan

How to turn the rule catalogs into a prioritized Phase-B backlog. Drives the porting work in
[roadmap.md](roadmap.md) Phase B; architecture in [hld.md](hld.md).

Source catalogs (reference data, already richly annotated):
- [ktlint-rules-catalog.md](ktlint-rules-catalog.md) — ~105 rules, multi-part.
- [detekt-rules-catalog.md](detekt-rules-catalog.md) — ~94 rules, multi-part.
- [diktat-unique-rules.md](diktat-unique-rules.md) — ~42, tagged for overlap with the above.

## Classification — reuse the catalog's own fields

Both catalogs already record the two fields we need, so this is a tagging pass, not a
re-analysis:

| Wrasse class | Derived from catalog field | Lands in | Host |
|---|---|---|---|
| **syntactic** | `Needs semantic info: none` (ktlint) / `Uses Analysis API: no` (detekt) | Phase B.1 / B.2 | A (lint), B (if autofix) |
| **inbound-resolution** | `Needs semantic info: type/symbol` / `Uses Analysis API: yes` | Phase B.3 | A + `SemanticWRule` |
| **outbound** | needs whole-program / cross-file reference info | **Parked** | — |
| **formatting** | ktlint `Category: wrapping/spacing/indent/blank-line` etc. | Phase C | B |

Cross-cut tags:
- **autofix?** — ktlint `Autocorrectable: yes/no`; detekt autoCorrect support. Drives Phase C.
- **complexity 1–3** — both catalogs carry it; do 1 before 3.
- **overlap** — `Has detekt equivalent` / `Has ktlint equivalent` / the diktat overlap table.
  Implement the union once under a single wrasse rule id; don't port duplicates twice.

> Most ktlint/detekt rules are `Needs semantic info: none` — i.e. **syntactic** — so the large
> majority is portable in Phase B before `SemanticWRule` even exists. Resolution rules are the
> minority and gate on the adapter work.

## Priority tiers

1. **B.1 — syntactic, non-autofix lint.** Highest ratio of value to infra (zero new infra; pure
   `NodeVisitor`/`FileVisitor`). The detekt smell/style catalog is mostly here.
2. **B.2 — syntactic, autofix-capable.** Same engine; the fix half ships in Phase C. Most ktlint
   formatting/spacing rules.
3. **B.3 — inbound-resolution.** Blocked on `SemanticWRule` + the LightTree↔FIR adapter. Smaller
   set; includes the import-optimization moat (Phase C flagship).
4. **Parked — outbound.** Tracked, not scheduled (see roadmap "Parked").

Within a tier: complexity 1 → 3, and implement overlapping ktlint/detekt/diktat rules as one
wrasse rule.

## Already shipped

`no-semicolons` (has the known statement-separator false positive — fix in B.1), `no-wildcard-imports`
(flag-only today; the *fix* is the Phase C moat), `trailing-newline`.

## Starter backlog (representative, not exhaustive)

A seed of high-value parity rules to begin B.1/B.2. Complete the full tagging by sweeping the
catalogs with the table above.

**B.1 syntactic lint (no autofix, complexity 1–2):**
- detekt: `BracesOnIfStatements`, `BracesOnWhenStatements`, `MagicNumber`, `ModifierOrder`,
  naming rules (`ClassNaming`, `FunctionNaming`, `VariableNaming`, `EnumNaming`, `PackageNaming`),
  empty-block family, `MaxLineLength`.
- diktat-unique worth keeping: `file-size`, `debug-print`, `when-must-have-else`,
  `long-numerical-values` (underscores), `string-concatenation`.

**B.2 syntactic autofix (formatting-adjacent, → Phase C):**
- ktlint: `no-multi-spaces`, `op-spacing`/`colon-spacing`/`keyword-spacing` (→ a `formatting-`
  spacing family), `modifier-order`, `trailing-comma-on-*`, `enum-wrapping`, `import-ordering`,
  `annotation` / `annotation-spacing`. (Indentation and argument/binary-expression wrapping are
  complexity-3 and interact heavily — schedule late in C.)

**B.3 inbound-resolution:**
- **Import optimization** (`no-wildcard-imports` autofix = star expansion; unused-import removal) —
  the flagship; needs resolved symbols.
- Any catalog rule tagged needs-type-info (the minority) — tag and pull in once `SemanticWRule`
  lands.

## Process to finish the plan

1. Sweep each catalog; for every rule fill: `wrasse-id`, class (syntactic/inbound/outbound/
   formatting), autofix?, complexity, overlap-with.
2. Collapse overlaps to one `wrasse-id`.
3. Drop outbound into Parked; order the rest by tier then complexity.
4. The result is the Phase-B issue list.
