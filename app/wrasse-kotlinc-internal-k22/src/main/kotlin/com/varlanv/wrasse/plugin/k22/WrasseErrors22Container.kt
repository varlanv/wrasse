package com.varlanv.wrasse.plugin.k22

import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers.TO_STRING
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.psi.KtElement

object WrasseErrors22Container : KtDiagnosticsContainer() {
    val WRASSE_ERROR by error1<KtElement, String>(SourceElementPositioningStrategies.DEFAULT)
    val WRASSE_WARNING by warning1<KtElement, String>(SourceElementPositioningStrategies.DEFAULT)

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = Renderers

    object Renderers : BaseDiagnosticRendererFactory() {
        override val MAP by KtDiagnosticFactoryToRendererMap("Wrasse") {
            it.put(WRASSE_ERROR, "wrasse: {0}", TO_STRING)
            it.put(WRASSE_WARNING, "wrasse: {0}", TO_STRING)
        }
    }
}
