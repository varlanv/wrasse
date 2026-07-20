# Wrasse — Autoformat Scope (rule-by-rule classification)

Classification of every rule in the three catalogs against the "one opinionated formatter"
design question: which rules dissolve into a single ktfmt/prettier-style printer, which stay
targeted autofixes, which are lint-only, and which get dropped. Produced 2026-07-18 from
[ktlint-rules-catalog.md](ktlint-rules-catalog.md), [detekt-rules-catalog.md](detekt-rules-catalog.md),
[diktat-unique-rules.md](diktat-unique-rules.md). Design context and bucket definitions:
[design.md](design.md) §6.

## Buckets

- **F — formatter**: dissolves into the opinionated printer. Not implemented as a `WRule` at all;
  becomes a branch of the DocBuilder / a property of the layout pass.
- **S — semantic fix engine**: needs FIR resolution. In practice one engine: imports
  (unused removal, star expansion, FQN→import, ordering after rewrite).
- **T — targeted safe autofix**: small, behavior-preserving, deterministic token add/remove/reorder.
  Individually toggleable, implemented as `WRule`s emitting edits via the EditPlan.
- **L — lint-only**: report, never autofix (naming, metrics, smells, judgment-shaped rewrites).
- **X — dropped**: not applicable / too niche / compiler-covered / outbound.

## Totals (255 rules)

| Catalog | F | S | T | L | X | Total |
|---|---|---|---|---|---|---|
| ktlint | 76 | 2 | 8 | 19 | 0 | 105 |
| detekt | 9 | 2 | 9 | 74 | 0 | 94 |
| diktat-unique | 7 | 0 | 3 | 36 | 10 | 56 |
| **Total** | **92** | **4** | **20** | **129** | **10** | **255** |

The 4 S entries collapse to **one ImportEngine** (unused-import ×2, wildcard expansion,
unnecessary-FQN). The 20 T entries overlap across catalogs and collapse to roughly **14 unique
fixes**. The 92 F entries are not 92 implementations — they are **one printer**.

## The printer's contract (resolves cross-catalog disagreements)

The printer may change **only**: whitespace, line breaks, indentation, blank lines, trailing
commas, and provably-redundant statement-separator semicolons. It never touches any other token.

Consequences, applied consistently where the per-catalog sweeps disagreed:

- **Brace insertion** (`if-else-bracing`, `multiline-if-else`, `multiline-loop`,
  `when-entry-bracing`, detekt `braces-on-*`) → **T**, not F. Prettier/ktfmt do not insert
  tokens the author didn't write; neither does the wrasse printer. Safe mechanical opt-in fixes.
- **`modifier-order`** → **T** (token reordering, not layout).
- **Redundant-syntax deletions** (`no-empty-class-body`, `no-unit-return`,
  `unnecessary-parentheses*`, `redundant-visibility-modifier`, `redundant-constructor-keyword`,
  `unnecessary-backticks`, `explicit-it-lambda-parameter`,
  `empty-default-constructor`, diktat `trivial-accessors`, `long-numerical-values`,
  `range-conventional`) → **T**.
- **Semicolons and trailing commas** → **F** (the explicitly whitelisted token exceptions,
  same as prettier's semicolon/quote handling in JS).
- **`max-line-length`** → F for the fix (wrapping), plus a residual **L** report for lines the
  printer cannot break (long string literals, URLs).
- **Comment interiors** (`comment-spacing`'s `//x` → `// x`) → keep in F but flagged: most
  printers leave comment token interiors alone; decide during Phase C.
- **`import-ordering`** → owned by the ImportEngine (it must re-sort after removal/expansion
  anyway); the printer treats the import list as engine-owned output.

## Style parameters (the whole option surface of the formatter)

From the ktlint config-variance analysis, the axes teams genuinely vary. Everything else is
fixed by fiat (defaults = official Kotlin style):

1. `indentWidth` (default 4; tabs not supported, by fiat)
2. `maxLineLength` (default 140)
3. `trailingCommas` (on/off; default on — call site and declaration site as one axis)
4. `importLayout` (default ASCII; the one high-variance knob)
5. Possibly: multiline-signature threshold (parameter count ≥ N forces multiline; default 1)

ktlint's `CODE_STYLE_PROPERTY` meta-knob (ktlint_official / intellij_idea / android_studio),
which gates ~10 wrapping/blank-line rules, **disappears entirely** — the single biggest
simplification the one-formatter decision buys. No per-rule format toggles exist.

---

## ktlint (105 rules)

| rule-id | bucket | reason |
|---|---|---|
| annotation | F | annotation line-wrapping is printer layout |
| annotation-spacing | F | blank-line/adjacency layout around annotations |
| argument-list-wrapping | F | argument wrapping is core printer output |
| backing-property-naming | L | rename needs cross-file changes; judgment |
| binary-expression-wrapping | F | line-length-driven wrapping, printer decides |
| blank-line-before-declaration | F | blank-line policy is printer-owned |
| blank-line-before-file-annotation | F | blank-line placement |
| blank-line-before-imports | F | blank-line placement |
| blank-line-before-package | F | blank-line placement |
| blank-line-between-when-conditions | F | blank-line policy inside when |
| block-comment-initial-star-alignment | F | comment star alignment is indentation |
| call-expression-wrapping | F | call/lambda wrapping, printer decides |
| chain-method-continuation | F | chain one-line-or-all-lines is printer layout |
| chain-wrapping | F | operator position at line breaks |
| class-naming | L | rename is cross-file; report only |
| class-signature | F | signature wrapping (content edits dropped per printer contract) |
| comment-spacing | F | space after // is spacing normalization (flagged: comment interior) |
| comment-wrapping | F | block-comment placement relative to code lines |
| context-receiver-list-wrapping | F | wrapping of context parameter lists |
| context-receiver-wrapping | F | wrapping of deprecated context receivers |
| enum-entry-name-case | L | naming; rename cross-file |
| enum-wrapping | F | one-entry-per-line layout decision |
| expression-operand-wrapping | F | operand wrapping in multiline expressions |
| filename | L | fix requires renaming the file itself |
| final-newline | F | trailing newline is printer output |
| function-expression-body | L | body-form rewrite plus type insertion; refactoring-shaped |
| function-literal | F | lambda params/arrow/block layout |
| function-naming | L | rename cross-file; test/factory heuristics |
| function-return-type-spacing | F | colon spacing |
| function-signature | F | signature wrapping is printer core |
| function-start-of-body-spacing | F | spacing around = and { |
| function-type-modifier-spacing | F | spacing |
| function-type-reference-spacing | F | spacing |
| fun-keyword-spacing | F | spacing |
| if-else-bracing | T | brace insertion: safe mechanical token add |
| if-else-wrapping | F | then/else newline layout |
| import-ordering | S | owned by ImportEngine (re-sorts after rewrite anyway) |
| indent | F | the printer literally is this rule |
| kdoc | L | misplaced KDoc; no safe auto-move |
| kdoc-wrapping | F | KDoc own-line placement |
| lambda-return | L | removing labeled return is refactoring-shaped |
| max-line-length | F | max width is the printer's input parameter (+ residual L report) |
| mixed-condition-operators | L | paren insertion encodes intent; judgment |
| modifier-list-spacing | F | spacing/newlines in modifier list |
| modifier-order | T | canonical safe token reordering |
| multiline-expression-wrapping | F | expression start-on-new-line layout |
| multiline-if-else | T | brace insertion: safe mechanical token add |
| multiline-loop | T | brace insertion: safe mechanical token add |
| no-blank-line-before-rbrace | F | blank-line policy |
| no-blank-line-in-list | F | blank-line policy in lists |
| no-blank-lines-in-chained-method-calls | F | blank-line policy in chains |
| no-consecutive-blank-lines | F | blank-line collapsing |
| no-consecutive-comments | L | comment structure; no mechanical fix |
| no-empty-class-body | T | removing empty {} is safe token removal |
| no-empty-file | L | only fix is deleting the file |
| no-empty-first-line-in-class-body | F | blank-line policy |
| no-empty-first-line-in-method-block | F | blank-line policy |
| no-line-break-after-else | F | line-break placement |
| no-line-break-before-assignment | F | line-break placement around = |
| no-multi-spaces | F | space collapsing |
| no-semi | F | printer owns semicolons (whitelisted token exception) |
| no-single-line-block-comment | L | rewrites comment tokens; kept out of T |
| no-trailing-spaces | F | trailing whitespace removal |
| no-unit-return | T | removing `: Unit` is safe token removal |
| no-unused-imports | S | correct removal needs resolution (ktlint has FPs) |
| no-wildcard-imports | S | star-expansion fix needs resolution |
| nullable-type-spacing | F | spacing before ? |
| package-import-spacing | F | blank line between package and imports |
| package-name | L | rename implies directory/reference changes |
| parameter-list-spacing | F | spacing in parameter lists |
| parameter-list-wrapping | F | parameter wrapping, printer core |
| parameter-wrapping | F | length-driven wrapping |
| property-naming | L | rename cross-file; immutability judgment |
| property-wrapping | F | length-driven wrapping |
| spacing-around-angle-brackets | F | spacing |
| colon-spacing | F | spacing |
| comma-spacing | F | spacing |
| curly-spacing | F | spacing |
| dot-spacing | F | spacing |
| double-colon-spacing | F | spacing |
| keyword-spacing | F | spacing |
| op-spacing | F | spacing |
| paren-spacing | F | spacing |
| range-spacing | F | spacing |
| square-brackets-spacing | F | spacing |
| unary-op-spacing | F | spacing |
| spacing-between-declarations-with-annotations | F | blank-line policy |
| spacing-between-declarations-with-comments | F | blank-line policy |
| spacing-between-function-name-and-opening-parenthesis | F | spacing |
| statement-wrapping | F | newlines after {, before }, after ; |
| string-template-indent | F | raw-string indentation layout |
| string-template | L | .toString() removal is call-removal; refactoring-shaped |
| then-spacing | F | spacing around then block |
| trailing-comma-on-call-site | F | printer owns trailing commas (style parameter) |
| trailing-comma-on-declaration-site | F | printer owns trailing commas (style parameter) |
| try-catch-finally-spacing | F | newline/space layout in try/catch |
| type-argument-comment | L | disallowed comment location; no safe move |
| type-argument-list-spacing | F | spacing |
| type-parameter-comment | L | disallowed comment location; no safe move |
| type-parameter-list-spacing | F | spacing |
| unnecessary-parentheses-before-trailing-lambda | T | removing empty () is safe token removal |
| value-argument-comment | L | disallowed comment location; no safe move |
| value-parameter-comment | L | disallowed comment location; no safe move |
| when-entry-bracing | T | brace insertion: safe mechanical token add |
| wrapping | F | the general newline-insertion engine, printer core |

(Note: `import-ordering` moved from the sweep's F to S per the printer contract above.)

## detekt (94 rules)

| rule-id | bucket | reason |
|---|---|---|
| also-could-be-apply | L | "use apply instead" refactoring suggestion |
| braces-on-if-statements | T | brace insertion/removal is safe mechanical content change |
| braces-on-when-statements | T | brace insertion/removal is safe mechanical content change |
| cascading-call-wrapping | F | call-chain line wrapping is the printer's decision |
| destructuring-declaration-with-too-many-entries | L | threshold-based design smell, no fix |
| equals-null-call | L | rewrite not strictly behavior-preserving; suggestion-shaped |
| equals-on-signature-line | F | `=` line placement is pure layout |
| explicit-it-lambda-multiple-parameters | L | fix requires inventing a new name |
| explicit-it-lambda-parameter | T | deleting redundant `it ->` is safe, unambiguous |
| forbidden-comment | L | policy check on comment content |
| forbidden-suppress | L | policy check, no sensible autofix |
| function-only-returning-constant | L | "make it const val" refactoring suggestion |
| max-line-length | F | printer owns line width via wrapping |
| may-be-constant | L | adding `const` changes binary/inlining semantics; suggestion |
| modifier-order | T | canonical token reordering (aligned with ktlint sweep) |
| multiline-raw-string-indentation | F | indentation concern (safe only under trimIndent; else skip) |
| nested-classes-visibility | L | visibility design smell |
| new-line-at-end-of-file | F | trailing newline is printer output invariant |
| no-tabs | F | printer emits spaces, tabs vanish |
| range-until-instead-of-range-to | L | "use ..< instead" refactoring suggestion |
| redundant-constructor-keyword | T | deleting redundant keyword, always safe |
| redundant-visibility-modifier | T | deleting default `public` is safe mechanical (honor Explicit API mode) |
| return-count | L | complexity-style metric, judgment |
| safe-cast | L | "use as? instead" refactoring suggestion |
| spacing-after-package-and-imports | F | blank-line policy, printer owns |
| string-should-be-raw-string | L | raw-string conversion is judgment-based rewrite |
| throws-count | L | metric threshold, no fix |
| trailing-whitespace | F | printer never emits trailing whitespace |
| trim-multiline-raw-string | L | adding trimIndent changes runtime string value |
| unnecessary-backticks | T | removing useless backticks is safe mechanical |
| unnecessary-fully-qualified-name | S | resolution-powered FQN-to-import rewrite (ImportEngine) |
| unnecessary-inheritance | L | matches detekt's own rule, which ships no autofix |
| unnecessary-parentheses | T | parser-verified useless parens, safe removal |
| unused-import | S | resolution-powered unused-import removal (ImportEngine) |
| unused-parameter | L | dead-code signal; deletion changes API, report only |
| unused-private-class | L | dead-code smell; deletion is a decision |
| use-let | L | "use ?.let instead" refactoring suggestion |
| class-naming | L | naming, rename is never automatic |
| constructor-parameter-naming | L | naming, rename is never automatic |
| enum-naming | L | naming, rename is never automatic |
| forbidden-class-name | L | naming policy, report only |
| function-name-max-length | L | naming metric, report only |
| function-name-min-length | L | naming metric, report only |
| function-naming | L | naming, rename is never automatic |
| function-parameter-naming | L | naming, rename is never automatic |
| invalid-package-declaration | L | fix means moving files/packages, judgment |
| lambda-parameter-naming | L | naming, rename is never automatic |
| no-name-shadowing | L | shadowing smell; fix requires rename |
| object-property-naming | L | naming, rename is never automatic |
| package-naming | L | naming, rename is never automatic |
| top-level-property-naming | L | naming, rename is never automatic |
| variable-max-length | L | naming metric, report only |
| variable-min-length | L | naming metric, report only |
| variable-naming | L | naming, rename is never automatic |
| absent-or-wrong-file-license | L | template policy; header content is human-owned |
| documentation-over-private-property | L | documentation design opinion |
| kdoc-references-non-public-property | L | doc correctness, rewrite needs judgment |
| outdated-documentation | L | doc-vs-signature drift, human must reconcile |
| undocumented-public-class | L | missing docs cannot be autogenerated |
| undocumented-public-function | L | missing docs cannot be autogenerated |
| undocumented-public-property | L | missing docs cannot be autogenerated |
| cognitive-complex-method | L | complexity metric, report only |
| complex-condition | L | complexity metric, report only |
| cyclomatic-complex-method | L | complexity metric, report only |
| labeled-expression | L | complexity smell, restructuring needed |
| global-coroutine-usage | L | design smell, structured-concurrency judgment |
| empty-class-block | T | deleting empty `{}` body is safe mechanical |
| empty-default-constructor | T | deleting redundant `()` constructor is safe |
| empty-do-while-block | L | empty block signals possible bug, report |
| empty-else-block | L | empty block signals possible bug, report |
| empty-finally-block | L | empty block signals possible bug, report |
| empty-for-block | L | empty block signals possible bug, report |
| empty-function-block | L | empty body may be intentional stub, report |
| empty-if-block | L | empty block signals possible bug, report |
| empty-init-block | L | likely leftover of deleted code, report |
| empty-kotlin-file | L | fix is deleting a file, human decision |
| empty-secondary-constructor | L | pointless constructor is smell, removal changes API |
| empty-try-block | L | empty block signals possible bug, report |
| empty-when-block | L | empty when signals possible bug, report |
| empty-while-block | L | empty block signals possible bug, report |
| exception-raised-in-unexpected-location | L | exception-design smell, report only |
| print-stack-trace | L | "use a logger" suggestion, judgment |
| rethrow-caught-exception | L | pointless try-catch smell, removal is refactoring |
| swallowed-exception | L | error-handling smell, needs human fix |
| throwing-exception-in-main | L | design smell, report only |
| forbidden-public-data-class | L | library API design policy, report only |
| library-entities-should-not-be-public | L | library API design policy, report only |
| array-primitive | L | inbound type resolution, but IntArray rewrite is refactoring |
| for-each-on-range | L | performance suggestion, loop rewrite is refactoring |
| unnecessary-part-of-binary-expression | L | duplicate operand likely a bug, report |
| invalid-range | L | likely bug, autofix cannot guess intent |
| missing-package-declaration | L | correct package name requires judgment |
| unconditional-jump-statement-in-loop | L | likely bug, report only |
| useless-postfix-expression | L | likely bug, autofix cannot guess intent |

(Note: `modifier-order` moved F→T for consistency with the printer contract.)

## diktat-unique (56 rules)

| rule-id | bucket | reason |
|---|---|---|
| identifier-naming (unique sub-checks) | L | rename fixes touch all usages; smart refactoring |
| package-naming (unique sub-checks) | L | changing package FQN breaks other files |
| comments (commented-out code) | L | deleting code is never a safe autofix |
| header-comment (unique sub-checks) | X | corporate copyright-year/header-KDoc policy; detekt covers rest |
| kdoc-comments-codeblocks-formatting | F | comment spacing and blank lines are formatter territory |
| kdoc-comments (@property/@param mgmt) | L | generating doc tags is content authoring |
| kdoc-formatting | F | tag order/spacing dissolve into KDoc-aware printing |
| kdoc-methods | L | missing-tag/trivial-KDoc detection; fix means writing docs |
| block-structure-braces | F | brace *placement* is core formatter output |
| boolean-expressions | L | algebraic rewriting is smart refactoring |
| class-like-structures | L | member reordering can change init order |
| collapse-if | L | merging conditions is structural refactoring |
| debug-print | L | print-call detection; removal never automatic |
| blank-lines | F | blank lines inside blocks; formatter owns |
| file-size | L | metric threshold report only |
| file-structure (block order) | F | package/import/code ordering is layout ordering |
| newlines (unique parts) | F | chain wrapping and newline placement; formatter owns |
| top-level-order | X | reordering changes top-level init order; too opinionated |
| local-variables | L | moving declarations is refactoring |
| long-numerical-values | T | inserting underscores is purely lexical, identity-preserving |
| nullable-type | L | changing declared type alters API surface |
| preview-annotation | X | Compose-framework-specific, too niche for general tool |
| range-conventional | T | `.rangeTo(b)` is definitionally `..`; textual identity rewrite |
| statement (multi-statements-per-line) | F | statement splitting/semicolons are formatter concerns |
| sort-rule | X | alphabetizing properties changes init order; hyper-opinionated |
| string-concatenation | L | template conversion is expression rewriting |
| when-must-have-else | L | adding else branch requires authored behavior |
| accurate-calculations | X | flags all float math; noisy military-spec heuristic |
| no-var-rule | L | val conversion needs reliable reassignment analysis |
| null-checks (unique parts) | L | `?.let`/elvis rewrites are smart refactoring |
| smart-cast | X | compiler already warns USELESS_CAST |
| type-alias | L | introducing typealias is authored design choice |
| variable-generic-type | L | removing type args relies on inference edge cases |
| sync-in-async | L | runBlocking-in-coroutine; report only |
| avoid-nested-functions | X | local functions are idiomatic Kotlin; anti-idiom rule |
| inverse-method | L | name-mapping without resolution can miscorrect user types |
| custom-label | L | label-usage style; report only |
| lambda-length | L | metric threshold report only |
| lambda-parameter-order | L | reordering params breaks call sites |
| overloading-default-values | L | merging overloads is API refactoring |
| parameter-name-in-outer-lambda | L | naming `it` requires human-chosen name |
| compact-initialization | L | apply-block wrapping is structural refactoring |
| data-classes | L | data class changes equals/hashCode semantics |
| inline-classes | L | value class conversion changes ABI |
| single-constructor | L | constructor restructuring is refactoring |
| single-init | L | merging init blocks moves executable code |
| stateless-class | L | class-to-object changes instantiation semantics |
| custom-getter-setter | X | bans idiomatic Kotlin computed properties |
| extension-functions-class-file | X | file-placement dogma; extensions legitimately live elsewhere |
| extension-functions-same-name | L | member-shadows-extension confusion; useful report |
| implicit-backing-property | L | naming-convention consistency check |
| getter-setter-fields | L | catches accessor self-recursion bug; fix changes behavior |
| run-in-script | X | wrap-kts-in-run{} is bizarre niche convention |
| trivial-accessors | T | removing `get() = field` is identity (bail on annotations/visibility) |
| last-index | L | `.length` may not be CharSequence without resolution |
| useless-supertype | L | needs supertype member knowledge; in-file heuristic unsafe |

## Hard calls worth revisiting during implementation

1. **Brace insertion family (T)** — could be F by fiat in a more aggressive formatter; kept T so
   the printer never inserts tokens. Revisit only if user demand shows people expect it.
2. **`comment-spacing` (F)** — touches comment token interior; most printers don't. Decide in
   Phase C; safe fallback is T.
3. **`multiline-raw-string-indentation` (F)** — only value-preserving under
   `trimIndent`/`trimMargin`; printer must detect and skip otherwise.
4. **`redundant-visibility-modifier` (T)** — must self-disable under Explicit API mode.
5. **`trivial-accessors` (T)** — must bail on accessors with visibility modifiers or annotations.
6. **`unused-parameter`/`no-name-shadowing` (L)** — partially covered by kotlinc warnings;
   verify added value before porting.
7. **`function-expression-body`, `string-template`, `no-single-line-block-comment` (L)** —
   mechanically fixable but excluded from T per the strict behavior/token contract; candidates
   if the T bar is ever relaxed.
