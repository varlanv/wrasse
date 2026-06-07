package com.varlanv.wrasse.plugin.internal

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers.TO_STRING
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.warning1
import org.jetbrains.kotlin.psi.KtElement

object WrasseErrors : KtDiagnosticsContainer() {
    val RESTRICTED_API by error1<KtElement, String>(SourceElementPositioningStrategies.DEFAULT)
    val WRASSE_WARNING by warning1<KtElement, String>(SourceElementPositioningStrategies.DEFAULT)

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = Renderers

    object Renderers : BaseDiagnosticRendererFactory() {
        override val MAP by KtDiagnosticFactoryToRendererMap("Wrasse") {
            it.put(RESTRICTED_API, "wrasse: {0}", TO_STRING)
            it.put(WRASSE_WARNING, "wrasse: {0}", TO_STRING)
        }
    }
}
