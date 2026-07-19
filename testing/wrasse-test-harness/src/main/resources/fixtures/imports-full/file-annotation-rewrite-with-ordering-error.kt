@file:OptIn(sample.marker.Marker::class)

package sample

import sample.aux.Widget
// fixture-aux-file: aux/Aux.kt
// fixture-aux-file: aux/AuxMarker.kt

val w: sample.aux.Widget = TODO()

// expect-error 1:13 no-unnecessary-fqn "Unnecessary fully qualified name"
// expect-error 7:8 no-unnecessary-fqn "Unnecessary fully qualified name"