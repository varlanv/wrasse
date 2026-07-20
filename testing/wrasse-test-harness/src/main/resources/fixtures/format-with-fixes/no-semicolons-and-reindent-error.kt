package sample

class Box {
  val x = 1;
  fun show() {
    println(x)
  }
}

// expect-error 4:12 no-semicolons "Unnecessary semicolon"
// expect-error 1:1 format "File is not wrasse-formatted"
