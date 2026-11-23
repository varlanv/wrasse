// fixture-aux-file: support/Va.java
package sample

val total = Va.sum(100, 200, 300, 400, 500, 600, 700, 800, 900)
val nums = Va.sum(1, 2, 3)
val labelled = Va.label("x", 100, 200, 300, 400, 500, 600, 700, 800, 900)

// expect-error 1:1 format "File is not wrasse-formatted"
