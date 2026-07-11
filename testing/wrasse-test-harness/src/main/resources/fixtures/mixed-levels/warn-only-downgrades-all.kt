// fixture-option: warn-only
package sample

val x = 1;
val y = 2;

// expect-warning 3:10 no-semicolons "Unnecessary semicolon"
// expect-warning 4:10 no-semicolons "Unnecessary semicolon"
