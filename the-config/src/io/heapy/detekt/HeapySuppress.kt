package io.heapy.detekt

/**
 * Approves a `@Suppress` on the same declaration.
 *
 * Retention is SOURCE, so nothing reaches the consumer's bytecode. It is also what
 * [AnnotationTarget.EXPRESSION] requires.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.EXPRESSION,
    AnnotationTarget.FILE,
)
@Retention(AnnotationRetention.SOURCE)
annotation class HeapySuppress(
    val reason: String,
)
