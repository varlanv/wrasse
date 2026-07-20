package com.varlanv.wrasse.adapter

import com.varlanv.wrasse.model.WNodeType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.lexer.KtTokens

object WNodeTypeMapping {

    private val map: Map<IElementType, WNodeType> = buildMap {
        // File structure
        put(KtNodeTypes.KT_FILE, WNodeType.FILE)
        put(KtNodeTypes.PACKAGE_DIRECTIVE, WNodeType.PACKAGE_DIRECTIVE)
        put(KtNodeTypes.IMPORT_LIST, WNodeType.IMPORT_LIST)
        put(KtNodeTypes.IMPORT_DIRECTIVE, WNodeType.IMPORT_DIRECTIVE)

        // Declarations
        put(KtNodeTypes.FUN, WNodeType.FUN)
        put(KtNodeTypes.CLASS, WNodeType.CLASS)
        put(KtNodeTypes.OBJECT_DECLARATION, WNodeType.OBJECT_DECLARATION)
        put(KtNodeTypes.PROPERTY, WNodeType.PROPERTY)
        put(KtNodeTypes.TYPEALIAS, WNodeType.TYPEALIAS)
        put(KtNodeTypes.VALUE_PARAMETER_LIST, WNodeType.VALUE_PARAMETER_LIST)
        put(KtNodeTypes.VALUE_PARAMETER, WNodeType.VALUE_PARAMETER)
        put(KtNodeTypes.TYPE_PARAMETER_LIST, WNodeType.TYPE_PARAMETER_LIST)
        put(KtNodeTypes.TYPE_PARAMETER, WNodeType.TYPE_PARAMETER)
        put(KtNodeTypes.CLASS_BODY, WNodeType.CLASS_BODY)
        put(KtNodeTypes.ENUM_ENTRY, WNodeType.ENUM_ENTRY)
        put(KtNodeTypes.ENUM_ENTRY_SUPERCLASS_REFERENCE_EXPRESSION, WNodeType.ENUM_ENTRY_SUPERCLASS_REFERENCE_EXPRESSION)
        put(KtNodeTypes.PRIMARY_CONSTRUCTOR, WNodeType.PRIMARY_CONSTRUCTOR)
        put(KtNodeTypes.SECONDARY_CONSTRUCTOR, WNodeType.SECONDARY_CONSTRUCTOR)
        put(KtNodeTypes.PROPERTY_ACCESSOR, WNodeType.PROPERTY_ACCESSOR)
        put(KtNodeTypes.CLASS_INITIALIZER, WNodeType.CLASS_INITIALIZER)
        put(KtNodeTypes.CONSTRUCTOR_CALLEE, WNodeType.CONSTRUCTOR_CALLEE)
        put(KtNodeTypes.CONSTRUCTOR_DELEGATION_CALL, WNodeType.CONSTRUCTOR_DELEGATION_CALL)
        put(KtNodeTypes.CONSTRUCTOR_DELEGATION_REFERENCE, WNodeType.CONSTRUCTOR_DELEGATION_REFERENCE)
        put(KtNodeTypes.PROPERTY_DELEGATE, WNodeType.PROPERTY_DELEGATE)
        put(KtNodeTypes.SUPER_TYPE_LIST, WNodeType.SUPER_TYPE_LIST)
        put(KtNodeTypes.SUPER_TYPE_ENTRY, WNodeType.SUPER_TYPE_ENTRY)
        put(KtNodeTypes.SUPER_TYPE_CALL_ENTRY, WNodeType.SUPER_TYPE_CALL_ENTRY)
        put(KtNodeTypes.INITIALIZER_LIST, WNodeType.INITIALIZER_LIST)
        put(KtNodeTypes.IMPORT_ALIAS, WNodeType.IMPORT_ALIAS)
        put(KtNodeTypes.FILE_ANNOTATION_LIST, WNodeType.FILE_ANNOTATION_LIST)

        // Modifiers & annotations
        put(KtNodeTypes.MODIFIER_LIST, WNodeType.MODIFIER_LIST)
        put(KtNodeTypes.ANNOTATION_ENTRY, WNodeType.ANNOTATION_ENTRY)
        put(KtNodeTypes.ANNOTATION_TARGET, WNodeType.ANNOTATION_TARGET)

        // Type references
        put(KtNodeTypes.TYPE_REFERENCE, WNodeType.TYPE_REFERENCE)
        put(KtNodeTypes.USER_TYPE, WNodeType.USER_TYPE)
        put(KtNodeTypes.NULLABLE_TYPE, WNodeType.NULLABLE_TYPE)
        put(KtNodeTypes.FUNCTION_TYPE, WNodeType.FUNCTION_TYPE)
        put(KtNodeTypes.TYPE_ARGUMENT_LIST, WNodeType.TYPE_ARGUMENT_LIST)
        put(KtNodeTypes.TYPE_PROJECTION, WNodeType.TYPE_PROJECTION)

        // Expressions
        put(KtNodeTypes.BLOCK, WNodeType.BLOCK)
        put(KtNodeTypes.LAMBDA_EXPRESSION, WNodeType.LAMBDA_EXPRESSION)
        put(KtNodeTypes.FUNCTION_LITERAL, WNodeType.FUNCTION_LITERAL)
        put(KtNodeTypes.CALL_EXPRESSION, WNodeType.CALL_EXPRESSION)
        put(KtNodeTypes.VALUE_ARGUMENT_LIST, WNodeType.VALUE_ARGUMENT_LIST)
        put(KtNodeTypes.VALUE_ARGUMENT, WNodeType.VALUE_ARGUMENT)
        put(KtNodeTypes.REFERENCE_EXPRESSION, WNodeType.REFERENCE_EXPRESSION)
        put(KtNodeTypes.DOT_QUALIFIED_EXPRESSION, WNodeType.DOT_QUALIFIED_EXPRESSION)
        put(KtNodeTypes.SAFE_ACCESS_EXPRESSION, WNodeType.SAFE_ACCESS_EXPRESSION)
        put(KtNodeTypes.BINARY_EXPRESSION, WNodeType.BINARY_EXPRESSION)
        put(KtNodeTypes.PREFIX_EXPRESSION, WNodeType.PREFIX_EXPRESSION)
        put(KtNodeTypes.POSTFIX_EXPRESSION, WNodeType.POSTFIX_EXPRESSION)
        put(KtNodeTypes.IF, WNodeType.IF)
        put(KtNodeTypes.WHEN, WNodeType.WHEN)
        put(KtNodeTypes.WHEN_ENTRY, WNodeType.WHEN_ENTRY)
        put(KtNodeTypes.WHEN_CONDITION_EXPRESSION, WNodeType.WHEN_CONDITION_EXPRESSION)
        put(KtNodeTypes.WHEN_CONDITION_IS_PATTERN, WNodeType.WHEN_CONDITION_IS_PATTERN)
        put(KtNodeTypes.FOR, WNodeType.FOR)
        put(KtNodeTypes.WHILE, WNodeType.WHILE)
        put(KtNodeTypes.DO_WHILE, WNodeType.DO_WHILE)
        put(KtNodeTypes.TRY, WNodeType.TRY)
        put(KtNodeTypes.CATCH, WNodeType.CATCH)
        put(KtNodeTypes.FINALLY, WNodeType.FINALLY)
        put(KtNodeTypes.RETURN, WNodeType.RETURN)
        put(KtNodeTypes.THROW, WNodeType.THROW)
        put(KtNodeTypes.BREAK, WNodeType.BREAK)
        put(KtNodeTypes.CONTINUE, WNodeType.CONTINUE)
        put(KtNodeTypes.IS_EXPRESSION, WNodeType.IS_EXPRESSION)
        put(KtNodeTypes.BINARY_WITH_TYPE, WNodeType.AS_EXPRESSION)
        put(KtNodeTypes.OBJECT_LITERAL, WNodeType.OBJECT_LITERAL)
        put(KtNodeTypes.THIS_EXPRESSION, WNodeType.THIS_EXPRESSION)
        put(KtNodeTypes.SUPER_EXPRESSION, WNodeType.SUPER_EXPRESSION)
        put(KtNodeTypes.PARENTHESIZED, WNodeType.PARENTHESIZED)
        put(KtNodeTypes.LABELED_EXPRESSION, WNodeType.LABELED_EXPRESSION)
        put(KtNodeTypes.OPERATION_REFERENCE, WNodeType.OPERATION_REFERENCE)
        put(KtNodeTypes.LABEL, WNodeType.LABEL)
        put(KtNodeTypes.LABEL_QUALIFIER, WNodeType.LABEL_QUALIFIER)
        put(KtNodeTypes.CONDITION, WNodeType.CONDITION)
        put(KtNodeTypes.THEN, WNodeType.THEN)
        put(KtNodeTypes.ELSE, WNodeType.ELSE)
        put(KtNodeTypes.BODY, WNodeType.BODY)
        put(KtNodeTypes.LOOP_RANGE, WNodeType.LOOP_RANGE)
        put(KtNodeTypes.WHEN_CONDITION_IN_RANGE, WNodeType.WHEN_CONDITION_IN_RANGE)
        put(KtNodeTypes.LAMBDA_ARGUMENT, WNodeType.LAMBDA_ARGUMENT)
        put(KtNodeTypes.DESTRUCTURING_DECLARATION, WNodeType.DESTRUCTURING_DECLARATION)
        put(KtNodeTypes.DESTRUCTURING_DECLARATION_ENTRY, WNodeType.DESTRUCTURING_DECLARATION_ENTRY)
        put(KtNodeTypes.ANNOTATED_EXPRESSION, WNodeType.ANNOTATED_EXPRESSION)

        // Literals
        put(KtNodeTypes.INTEGER_CONSTANT, WNodeType.INTEGER_CONSTANT)
        put(KtNodeTypes.FLOAT_CONSTANT, WNodeType.FLOAT_CONSTANT)
        put(KtNodeTypes.CHARACTER_CONSTANT, WNodeType.CHARACTER_CONSTANT)
        put(KtNodeTypes.BOOLEAN_CONSTANT, WNodeType.BOOLEAN_CONSTANT)
        put(KtNodeTypes.NULL, WNodeType.NULL)
        put(KtNodeTypes.STRING_TEMPLATE, WNodeType.STRING_TEMPLATE)
        put(KtNodeTypes.LONG_STRING_TEMPLATE_ENTRY, WNodeType.LONG_STRING_TEMPLATE_ENTRY)
        put(KtNodeTypes.SHORT_STRING_TEMPLATE_ENTRY, WNodeType.SHORT_STRING_TEMPLATE_ENTRY)
        put(KtNodeTypes.LITERAL_STRING_TEMPLATE_ENTRY, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY)
        put(KtNodeTypes.ESCAPE_STRING_TEMPLATE_ENTRY, WNodeType.ESCAPE_STRING_TEMPLATE_ENTRY)

        // Tokens - string parts
        put(KtTokens.OPEN_QUOTE, WNodeType.OPEN_QUOTE)
        put(KtTokens.CLOSING_QUOTE, WNodeType.CLOSING_QUOTE)
        put(KtTokens.REGULAR_STRING_PART, WNodeType.REGULAR_STRING_PART)
        put(KtTokens.SHORT_TEMPLATE_ENTRY_START, WNodeType.SHORT_TEMPLATE_ENTRY_START)
        put(KtTokens.LONG_TEMPLATE_ENTRY_START, WNodeType.LONG_TEMPLATE_ENTRY_START)
        put(KtTokens.LONG_TEMPLATE_ENTRY_END, WNodeType.LONG_TEMPLATE_ENTRY_END)
        put(KtTokens.INTEGER_LITERAL, WNodeType.INTEGER_LITERAL)
        put(KtTokens.FLOAT_LITERAL, WNodeType.FLOAT_LITERAL)
        put(KtTokens.CHARACTER_LITERAL, WNodeType.CHARACTER_LITERAL)

        // Tokens - identifiers and whitespace
        put(KtTokens.IDENTIFIER, WNodeType.IDENTIFIER)
        put(KtTokens.WHITE_SPACE, WNodeType.WHITE_SPACE)

        // Tokens - comments
        put(KtTokens.EOL_COMMENT, WNodeType.EOL_COMMENT)
        put(KtTokens.BLOCK_COMMENT, WNodeType.BLOCK_COMMENT)
        put(KDocTokens.KDOC, WNodeType.KDOC)

        // Tokens - braces and brackets
        put(KtTokens.LPAR, WNodeType.LPAR)
        put(KtTokens.RPAR, WNodeType.RPAR)
        put(KtTokens.LBRACE, WNodeType.LBRACE)
        put(KtTokens.RBRACE, WNodeType.RBRACE)
        put(KtTokens.LBRACKET, WNodeType.LBRACKET)
        put(KtTokens.RBRACKET, WNodeType.RBRACKET)

        // Tokens - punctuation
        put(KtTokens.COMMA, WNodeType.COMMA)
        put(KtTokens.DOT, WNodeType.DOT)
        put(KtTokens.SAFE_ACCESS, WNodeType.SAFE_ACCESS)
        put(KtTokens.ELVIS, WNodeType.ELVIS)
        put(KtTokens.RANGE, WNodeType.RANGE)
        put(KtTokens.COLONCOLON, WNodeType.COLONCOLON)
        put(KtTokens.COLON, WNodeType.COLON)
        put(KtTokens.SEMICOLON, WNodeType.SEMICOLON)
        put(KtTokens.ARROW, WNodeType.ARROW)
        put(KtTokens.DOUBLE_ARROW, WNodeType.DOUBLE_ARROW)

        // Tokens - operators
        put(KtTokens.EQ, WNodeType.EQ)
        put(KtTokens.EQEQ, WNodeType.EQEQ)
        put(KtTokens.EXCLEQ, WNodeType.EXCLEQ)
        put(KtTokens.LT, WNodeType.LT)
        put(KtTokens.GT, WNodeType.GT)
        put(KtTokens.LTEQ, WNodeType.LTEQ)
        put(KtTokens.GTEQ, WNodeType.GTEQ)
        put(KtTokens.PLUS, WNodeType.PLUS)
        put(KtTokens.MINUS, WNodeType.MINUS)
        put(KtTokens.MUL, WNodeType.MUL)
        put(KtTokens.DIV, WNodeType.DIV)
        put(KtTokens.PERC, WNodeType.PERC)
        put(KtTokens.PLUSEQ, WNodeType.PLUSEQ)
        put(KtTokens.MINUSEQ, WNodeType.MINUSEQ)
        put(KtTokens.MULTEQ, WNodeType.MULEQ)
        put(KtTokens.DIVEQ, WNodeType.DIVEQ)
        put(KtTokens.PERCEQ, WNodeType.PERCEQ)
        put(KtTokens.ANDAND, WNodeType.ANDAND)
        put(KtTokens.OROR, WNodeType.OROR)
        put(KtTokens.EXCL, WNodeType.EXCL)
        put(KtTokens.PLUSPLUS, WNodeType.PLUSPLUS)
        put(KtTokens.MINUSMINUS, WNodeType.MINUSMINUS)
        put(KtTokens.EXCLEXCL, WNodeType.EXCLEXCL)
        put(KtTokens.AT, WNodeType.AT)
        put(KtTokens.QUEST, WNodeType.QUEST)
        put(KtTokens.AS_SAFE, WNodeType.AS_SAFE)

        // Tokens - keywords
        put(KtTokens.FUN_KEYWORD, WNodeType.KW_FUN)
        put(KtTokens.VAL_KEYWORD, WNodeType.KW_VAL)
        put(KtTokens.VAR_KEYWORD, WNodeType.KW_VAR)
        put(KtTokens.CLASS_KEYWORD, WNodeType.KW_CLASS)
        put(KtTokens.INTERFACE_KEYWORD, WNodeType.KW_INTERFACE)
        put(KtTokens.OBJECT_KEYWORD, WNodeType.KW_OBJECT)
        put(KtTokens.IF_KEYWORD, WNodeType.KW_IF)
        put(KtTokens.ELSE_KEYWORD, WNodeType.KW_ELSE)
        put(KtTokens.WHEN_KEYWORD, WNodeType.KW_WHEN)
        put(KtTokens.FOR_KEYWORD, WNodeType.KW_FOR)
        put(KtTokens.WHILE_KEYWORD, WNodeType.KW_WHILE)
        put(KtTokens.DO_KEYWORD, WNodeType.KW_DO)
        put(KtTokens.RETURN_KEYWORD, WNodeType.KW_RETURN)
        put(KtTokens.THROW_KEYWORD, WNodeType.KW_THROW)
        put(KtTokens.BREAK_KEYWORD, WNodeType.KW_BREAK)
        put(KtTokens.CONTINUE_KEYWORD, WNodeType.KW_CONTINUE)
        put(KtTokens.TRY_KEYWORD, WNodeType.KW_TRY)
        put(KtTokens.CATCH_KEYWORD, WNodeType.KW_CATCH)
        put(KtTokens.FINALLY_KEYWORD, WNodeType.KW_FINALLY)
        put(KtTokens.IN_KEYWORD, WNodeType.KW_IN)
        put(KtTokens.IS_KEYWORD, WNodeType.KW_IS)
        put(KtTokens.AS_KEYWORD, WNodeType.KW_AS)
        put(KtTokens.NULL_KEYWORD, WNodeType.KW_NULL)
        put(KtTokens.TRUE_KEYWORD, WNodeType.KW_TRUE)
        put(KtTokens.FALSE_KEYWORD, WNodeType.KW_FALSE)
        put(KtTokens.THIS_KEYWORD, WNodeType.KW_THIS)
        put(KtTokens.SUPER_KEYWORD, WNodeType.KW_SUPER)
        put(KtTokens.PACKAGE_KEYWORD, WNodeType.KW_PACKAGE)
        put(KtTokens.IMPORT_KEYWORD, WNodeType.KW_IMPORT)
        put(KtTokens.PUBLIC_KEYWORD, WNodeType.KW_PUBLIC)
        put(KtTokens.PRIVATE_KEYWORD, WNodeType.KW_PRIVATE)
        put(KtTokens.PROTECTED_KEYWORD, WNodeType.KW_PROTECTED)
        put(KtTokens.INTERNAL_KEYWORD, WNodeType.KW_INTERNAL)
        put(KtTokens.OPEN_KEYWORD, WNodeType.KW_OPEN)
        put(KtTokens.ABSTRACT_KEYWORD, WNodeType.KW_ABSTRACT)
        put(KtTokens.SEALED_KEYWORD, WNodeType.KW_SEALED)
        put(KtTokens.DATA_KEYWORD, WNodeType.KW_DATA)
        put(KtTokens.OVERRIDE_KEYWORD, WNodeType.KW_OVERRIDE)
        put(KtTokens.SUSPEND_KEYWORD, WNodeType.KW_SUSPEND)
        put(KtTokens.INLINE_KEYWORD, WNodeType.KW_INLINE)
        put(KtTokens.TAILREC_KEYWORD, WNodeType.KW_TAILREC)
        put(KtTokens.OPERATOR_KEYWORD, WNodeType.KW_OPERATOR)
        put(KtTokens.INFIX_KEYWORD, WNodeType.KW_INFIX)
        put(KtTokens.COMPANION_KEYWORD, WNodeType.KW_COMPANION)
        put(KtTokens.CONST_KEYWORD, WNodeType.KW_CONST)
        put(KtTokens.LATEINIT_KEYWORD, WNodeType.KW_LATEINIT)
        put(KtTokens.FINAL_KEYWORD, WNodeType.KW_FINAL)
        put(KtTokens.INNER_KEYWORD, WNodeType.KW_INNER)
        put(KtTokens.EXTERNAL_KEYWORD, WNodeType.KW_EXTERNAL)
        put(KtTokens.EXPECT_KEYWORD, WNodeType.KW_EXPECT)
        put(KtTokens.ACTUAL_KEYWORD, WNodeType.KW_ACTUAL)
        put(KtTokens.ENUM_KEYWORD, WNodeType.KW_ENUM)
        put(KtTokens.TYPE_ALIAS_KEYWORD, WNodeType.KW_TYPEALIAS)
        put(KtTokens.FILE_KEYWORD, WNodeType.KW_FILE)
        put(KtTokens.FIELD_KEYWORD, WNodeType.KW_FIELD)
        put(KtTokens.BY_KEYWORD, WNodeType.KW_BY)
        put(KtTokens.CONSTRUCTOR_KEYWORD, WNodeType.KW_CONSTRUCTOR)
        put(KtTokens.INIT_KEYWORD, WNodeType.KW_INIT)
        put(KtTokens.OUT_KEYWORD, WNodeType.KW_OUT)
        put(KtTokens.VARARG_KEYWORD, WNodeType.KW_VARARG)
        put(KtTokens.REIFIED_KEYWORD, WNodeType.KW_REIFIED)
        put(KtTokens.ANNOTATION_KEYWORD, WNodeType.KW_ANNOTATION)
        put(KtTokens.GET_KEYWORD, WNodeType.KW_GET)
        put(KtTokens.SET_KEYWORD, WNodeType.KW_SET)
    }

    /**
     * [IElementType.getIndex] is a JVM-process-local short assigned at registration time —
     * stable for the lifetime of this JVM, but not portable across JVMs or compiler versions.
     * Built lazily from [map] (the source of truth) on first use in this JVM, sized to the
     * highest index among [map]'s own keys, so every mapped key is guaranteed to fit.
     */
    private val indexed: Array<WNodeType?> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        var maxIndex = -1
        for (key in map.keys) {
            val index = key.index.toInt()
            if (index > maxIndex) maxIndex = index
        }
        val table = arrayOfNulls<WNodeType>(maxIndex + 1)
        for ((key, value) in map) {
            val index = key.index.toInt()
            if (index >= 0) table[index] = value
        }
        table
    }

    fun map(elementType: IElementType): WNodeType {
        val index = elementType.index.toInt()
        if (index < 0) return map[elementType] ?: WNodeType.UNKNOWN
        val table = indexed
        return if (index < table.size) table[index] ?: WNodeType.UNKNOWN else WNodeType.UNKNOWN
    }
}
