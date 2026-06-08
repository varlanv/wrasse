package com.varlanv.wrasse.plugin.k20

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers.TO_STRING
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.warning1
import org.jetbrains.kotlin.psi.KtElement

object WrasseErrors20 {
    val WRASSE_ERROR by error1<KtElement, String>(SourceElementPositioningStrategies.DEFAULT)
    val WRASSE_WARNING by warning1<KtElement, String>(SourceElementPositioningStrategies.DEFAULT)

    object Renderers : BaseDiagnosticRendererFactory() {
        override val MAP = KtDiagnosticFactoryToRendererMap("Wrasse").also {
            it.put(WRASSE_ERROR, "wrasse: {0}", TO_STRING)
            it.put(WRASSE_WARNING, "wrasse: {0}", TO_STRING)
        }
    }
}
