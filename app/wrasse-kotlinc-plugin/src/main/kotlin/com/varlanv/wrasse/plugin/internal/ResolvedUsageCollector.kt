package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.model.WCallableUsage
import com.varlanv.wrasse.model.WResolvedUsage
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.expressions.FirErrorResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.references.FirErrorNamedReference
import org.jetbrains.kotlin.fir.references.FirPropertyWithExplicitBackingFieldResolvedNamedReference
import org.jetbrains.kotlin.fir.references.FirResolvedCallableReference
import org.jetbrains.kotlin.fir.references.FirResolvedErrorReference
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeErrorType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirErrorTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.name.CallableId

object ResolvedUsageCollector {

    @OptIn(DirectDeclarationsAccess::class)
    fun collect(file: FirFile): WResolvedUsage =
        runCatching {
            val visitor = UsageVisitor()
            for (annotation in file.annotations) {
                annotation.accept(visitor)
            }
            for (declaration in file.declarations) {
                declaration.accept(visitor)
            }
            WResolvedUsage(
                classifiers = visitor.classifiers,
                callables = visitor.callables,
                hasResolutionErrors = visitor.hasErrors,
            )
        }.getOrElse {
            WResolvedUsage(emptySet(), emptySet(), hasResolutionErrors = true)
        }

    private class UsageVisitor : FirVisitorVoid() {
        val classifiers = mutableSetOf<String>()
        val callables = mutableSetOf<WCallableUsage>()
        var hasErrors = false

        override fun visitElement(element: FirElement) {
            element.acceptChildren(this)
        }

        override fun visitResolvedNamedReference(resolvedNamedReference: FirResolvedNamedReference) {
            collectCallableUsage(resolvedNamedReference)
            visitElement(resolvedNamedReference)
        }

        override fun visitResolvedCallableReference(resolvedCallableReference: FirResolvedCallableReference) {
            collectCallableUsage(resolvedCallableReference)
            visitElement(resolvedCallableReference)
        }

        override fun visitPropertyWithExplicitBackingFieldResolvedNamedReference(
            propertyWithExplicitBackingFieldResolvedNamedReference: FirPropertyWithExplicitBackingFieldResolvedNamedReference,
        ) {
            collectCallableUsage(propertyWithExplicitBackingFieldResolvedNamedReference)
            visitElement(propertyWithExplicitBackingFieldResolvedNamedReference)
        }

        override fun visitResolvedErrorReference(resolvedErrorReference: FirResolvedErrorReference) {
            hasErrors = true
            visitElement(resolvedErrorReference)
        }

        override fun visitErrorNamedReference(errorNamedReference: FirErrorNamedReference) {
            hasErrors = true
            visitElement(errorNamedReference)
        }

        private fun collectCallableUsage(resolvedNamedReference: FirResolvedNamedReference) {
            val symbol = resolvedNamedReference.resolvedSymbol
            if (symbol is FirCallableSymbol<*>) {
                val callableId = symbol.callableId
                if (callableId != null && callableId.packageName != CallableId.PACKAGE_FQ_NAME_FOR_LOCAL) {
                    callables.add(toCallableUsage(callableId))
                }
            }
        }

        override fun visitResolvedTypeRef(resolvedTypeRef: FirResolvedTypeRef) {
            collectConeType(resolvedTypeRef.coneType)
            visitElement(resolvedTypeRef)
        }

        override fun visitErrorTypeRef(errorTypeRef: FirErrorTypeRef) {
            hasErrors = true
            visitElement(errorTypeRef)
        }

        override fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier) {
            resolvedQualifier.classId?.let { classifiers.add(it.asFqNameString()) }
            visitElement(resolvedQualifier)
        }

        override fun visitErrorResolvedQualifier(errorResolvedQualifier: FirErrorResolvedQualifier) {
            hasErrors = true
            visitElement(errorResolvedQualifier)
        }

        private fun collectConeType(coneType: ConeKotlinType) {
            when (coneType) {
                is ConeErrorType -> hasErrors = true
                is ConeClassLikeType -> {
                    classifiers.add(coneType.lookupTag.classId.asFqNameString())
                    for (typeArgument in coneType.typeArguments) {
                        typeArgument.type?.let { collectConeType(it) }
                    }
                }

                else -> {
                    for (typeArgument in coneType.typeArguments) {
                        typeArgument.type?.let { collectConeType(it) }
                    }
                }
            }
        }

        private fun toCallableUsage(callableId: CallableId): WCallableUsage {
            val classFqName = callableId.classId?.asFqNameString()
            return WCallableUsage(
                packageFqName = callableId.packageName.asString(),
                classFqName = classFqName,
                name = callableId.callableName.asString(),
            )
        }
    }
}
