package sample

val r = 0.rangeTo(1000000)

// fixture-option: multi-pass-fix
// expect-error 3:9 range-conventional "Replace rangeTo call with the .. operator"
// expect-error 3:19 long-numerical-values "Long numerical literal without underscore separators"
// expect-error 1:1 format "File is not wrasse-formatted"
