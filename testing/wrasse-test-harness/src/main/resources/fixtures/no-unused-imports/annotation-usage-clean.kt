package sample

import sample.auxann.Marker
import sample.auxann.Tagged

// fixture-aux-file: aux/Annotations.kt

@Marker
class Bare

@Tagged(Marker())
class Attributed

// expect-clean
