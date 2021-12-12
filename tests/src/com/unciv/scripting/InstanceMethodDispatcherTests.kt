package com.unciv.scripting

import com.unciv.scripting.reflection.Reflection.InstanceMethodDispatcher
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith


private fun info(message: Any?) { } // Originally this was a KTS where I would just print the args. Replacing the printlns with noops keeps around the info passed to them, and looks cleaner/is better understood by IDEs than commenting.

private typealias TestMethodResult = Unit // To make it easier to switch out in case it's decided to use the Signature class defined below and its assertion methods.

//private fun error(detailMessage: Any? = null): Nothing = throw AssertionError(detailMessage) // To save a tiny bit of (keyboard) typing


@RunWith(GdxTestRunner::class)
class InstanceMethodDispatcherTests {
    
    private fun assertError(call: () -> Unit) {
        Assert.assertThrows(IllegalArgumentException::class.java, call)
    }

    private val testInstance = TestClasses.TestInstance()

    private val testInstanceDispatcherMeth = InstanceMethodDispatcher(testInstance, "meth")
    private val testInstanceDipsatcherMeth2 = InstanceMethodDispatcher(testInstance, "meth2")
    private val testInstanceDispatcherMeth3 = InstanceMethodDispatcher(testInstance, "meth3")

    private val testInstanceGenericInt = TestClasses.TestInstanceGeneric<Int>()

    private val testInstanceGenericIntDispatcherMeth = InstanceMethodDispatcher(testInstanceGenericInt, "meth")
    private val testInstanceGenericIntDispatcherMeth2 = InstanceMethodDispatcher(testInstanceGenericInt, "meth2")

    private val testInstanceDispatcherLenient = InstanceMethodDispatcher(testInstance, "meth", true)

    private val testInstanceDispatcherMeth3ResolveAmbiguous = InstanceMethodDispatcher(testInstance, "meth3", resolveAmbiguousSpecificity=true)


    // Regular tests.

    @Test
    fun testBasicInt() = testInstanceDispatcherMeth.call<TestMethodResult>(arrayOf(1))
    @Test
    fun testBasicString() = testInstanceDispatcherMeth.call<TestMethodResult>(arrayOf("A"))
    @Test
    fun testBasicIntStringlist() = testInstanceDispatcherMeth.call<TestMethodResult>(arrayOf(2, listOf("Test", "List")))
    @Test
    fun testBasicStringStringlist() = testInstanceDispatcherMeth.call<TestMethodResult>(arrayOf("B", listOf("Test", "List")))
    @Test
    fun testBasicStringNull() = testInstanceDispatcherMeth.call<TestMethodResult>(arrayOf("B", null))

    //@Test
    //fun testBasic2Null() = testInstanceDipsatcherMeth2.call<TestMethodResult>(arrayOf(null)) // Worked usually but not always when these tests were still a KTS. Possible compiler heisenbug here. Seems to be an internal Kotlin error caused when saving KParameter.type in a list under checkParameterMatches.
    @Test
    fun testBasic2StringFailAmbiguous() = assertError {
         testInstanceDipsatcherMeth2.call<TestMethodResult>(arrayOf("Fail"))
    }

    @Test
    fun testBasic3Stringlist() = testInstanceDispatcherMeth3.call<TestMethodResult>(arrayOf(listOf("A"))) // Should always work, as only the signature for the base class should match.
    @Test
    fun testBasic3StringarraylistFailAmbiguous() = assertError {
         testInstanceDispatcherMeth3.call<TestMethodResult>(arrayOf(arrayListOf("B")))
    } // Requires subtype resolution.
    @Test
    fun testBasic3StringcustomlistFailAmbiguous() = assertError {
         testInstanceDispatcherMeth3.call<TestMethodResult>(arrayOf(TestClasses.CustomList<String>()))
    }


    // Generic tests.

    @Test
    fun testGenericInt() = testInstanceGenericIntDispatcherMeth.call<TestMethodResult>(arrayOf(1))
    @Test
    fun testGenericIntInt() = testInstanceGenericIntDispatcherMeth.call<TestMethodResult>(arrayOf(2, 5))
    @Test
    fun testGenericStringInt() = testInstanceGenericIntDispatcherMeth.call<TestMethodResult>(arrayOf("A", 5))
    @Test
    fun testGenericStringNull() = testInstanceGenericIntDispatcherMeth.call<TestMethodResult>(arrayOf("B", null))
    //@Test //Huh. Apparently typing the object to Int doesn't stop a String from being used at its generic place. So, what? Erased generics have no influence at runtime, and typing using them is just a code-hinting/code-linting sham?
    fun testGenericStringStringFailNomatch() = assertError {
         testInstanceGenericIntDispatcherMeth.call<TestMethodResult>(arrayOf("B", "Fail"))
    }

    @Test
    fun testGeneric2IntFailAmbiguous() = assertError {
         testInstanceGenericIntDispatcherMeth2.call<TestMethodResult>(arrayOf(3))
    }
    @Test //This last one seems to work or break when you recompile. Actually I'm not sure dispatch resolution in this case is supported by the language.
    fun testGeneric2Null() = testInstanceGenericIntDispatcherMeth2.call<TestMethodResult>(arrayOf(null))


    // Numeric tests.

    @Test
    fun testNumericFloatFailNomatch() = assertError {
         testInstanceDispatcherMeth.call<TestMethodResult>(arrayOf(10.0f))
    }
    @Test
    fun testNumericLongFailNomatch() = assertError {
         testInstanceDispatcherMeth.call<TestMethodResult>(arrayOf(11L))
    }
    @Test
    fun testNumericDoubleNomatch() = assertError {
         testInstanceDispatcherMeth.call<TestMethodResult>(arrayOf(12.5))
    }
    @Test
    fun testLenientInt() = testInstanceDispatcherLenient.call<TestMethodResult>(arrayOf(15))
    /*dispnmeth.call<TestMethodResult>(arrayOf(16.0f)) // TODO: Test casts and order?
    dispnmeth.call<TestMethodResult>(arrayOf(17L))
    dispnmeth.call<TestMethodResult>(arrayOf(18.5))*/ //
    
    
    // Ambiguous tests.
    
    @Test
    fun testAmbiguous3Stringlist() = testInstanceDispatcherMeth3ResolveAmbiguous.call<TestMethodResult>(arrayOf(listOf("A"))) // Only one method compatible at all.
    @Test
    fun testAmbiguous3Stringarraylist() = testInstanceDispatcherMeth3ResolveAmbiguous.call<TestMethodResult>(arrayOf(arrayListOf("B"))) // Two methods compatible, but one is most specific.
    @Test
    fun testAmbiguous3Stringcustomlist() = testInstanceDispatcherMeth3ResolveAmbiguous.call<TestMethodResult>(arrayOf(TestClasses.CustomList<String>())) // Three methods compatible, but one is most specific.

}

object TestClasses {

    class CustomList<E> : ArrayList<E>()

//    class Signature(val types: List<KType?>) { // TODO: Could use this to make sure the ambiguity tests are all going to the right method.
//        companion object {
//            fun fromArgs(vararg args: Any?) = Signature(args.map { if (it == null) null else it::class.starProjectedType }.toList())
//            fun fromFunction(func: KFunction<*>) = func.parameters.map { it.type }
//        }
//        fun assertEquals(vararg checkTypes: KType?) = checkTypes.size == types.size && (checkTypes.toList() zip types).all { (arg, param) -> arg == param }
//        fun assertIsSuperOf(vararg checkTypes: KType?) = checkTypes.size == types.size && (checkTypes.toList() zip types).all { (arg, param) -> if (arg == null || param == null) arg == param else arg.isSubtypeOf(param) }
//    }


    @Suppress("unused")
    class TestInstance {
        fun meth(a:Int) = info("Int: ${a::class} $a")
        fun meth(a:String) = info("String: ${a::class} $a")
        fun meth(a:Int, b:Any) = info("Int: ${a::class} ${a}; Any: ${b::class} $b")
        //    fun meth(a:Int, b:Any?) {
//        //This should fail if enabled at the same time as the one above. due to multiple signatures. // TODO: JvmName?
//        info("Int: ${a::class} ${a}; Any?: ${b!!::class} ${b}")
//    }
        fun meth(a:String, b:Any?) = info("String: ${a::class} ${a}; Any?: ${if (b == null) null else b::class} $b")

        fun meth2(a: Any) = info("Any: ${a::class} $a")
        @JvmName("meth2Nullable") // Wasn't needed when running with kotlinc -script.
        fun meth2(a: Any?) = info("Any?: ${if (a == null) null else a::class} $a")

        fun meth3(a: List<*>) = info("List<*>: ${a::class} $a")
        //    fun meth3(a: MutableList<Any>) { // Apparently the jvmErasure for MutableList is just List. So having both makes resolutions ambiguous.
//        info("MutableList<*>: ${a::class} ${a}")
//    }
        fun meth3(a: ArrayList<*>) = info("ArrayList<*>: ${a::class} $a")
        fun meth3(a: CustomList<*>) = info("testlist<*>: ${a::class} $a")
    }

    @Suppress("unused")
    class TestInstanceGeneric<T: Any> { //Non-nullable upper bounds on T to test that T? is recognized as a distinct signature from T when calling with null.
        fun meth(a: T) = info("T: ${a::class} $a")
        fun meth(a:Int, b:T) = info("Int: ${a::class} ${a}; T: ${b::class} $b")
        fun meth(a:String, b:T?) = info("String: ${a::class} ${a}; T?: ${if (b == null) null else b::class} $b")

        fun meth2(a: T) = info("T: ${a::class} $a")
        @JvmName("meth2Nullable") // Wasn't needed when running with kotlinc -script.
        fun meth2(b: T?) = info("T?: ${if (b == null) null else b::class} $b")
    }
}
