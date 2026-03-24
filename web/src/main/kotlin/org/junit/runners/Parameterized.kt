package org.junit.runners

import kotlin.reflect.KClass

open class Parameterized {
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Parameters(val name: String = "")

    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class UseParametersRunnerFactory(val value: KClass<*>)
}
