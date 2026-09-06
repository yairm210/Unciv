package com.unciv.view

import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.battle.AttackRecorder
import com.unciv.logic.battle.AttackEvent
import com.unciv.logic.battle.AttackInterception
import com.unciv.logic.battle.AttackParticipant
import com.unciv.logic.map.HexCoord
import net.bytebuddy.jar.asm.ClassReader
import net.bytebuddy.jar.asm.ClassVisitor
import net.bytebuddy.jar.asm.FieldVisitor
import net.bytebuddy.jar.asm.Handle
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.jar.asm.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.jar.JarFile
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaMethod

/**
 * Kotlin internal visibility does not separate the UI from the engine in the core module.
 * Check the actual compiled callers, including generated code and method references, to keep
 * implementation-only access inside the deliberately small attack-history boundary.
 * This does not assert that unrelated existing raw-model View getters have been removed.
 */
class AttackEventsViewBoundaryTest {
    @Test
    fun `history storage is private and has no raw retrieval method`() {
        val storage = GameInfo::class.java.getDeclaredField("attackEvents")
        assertTrue(Modifier.isPrivate(storage.modifiers))
        assertEquals(KVisibility.PRIVATE,
            GameInfo::class.declaredMemberProperties.single { it.name == "attackEvents" }.visibility)
        assertFalse(GameInfo::class.java.methods.any {
            it.name.startsWith("getAttackEvents") || it.name.startsWith("setAttackEvents")
        })

        val engineMethods = GameInfo::class.declaredMemberFunctions.filter {
            it.name in setOf("storeAttack", "expireAttackEventsFor", "createAttackEventsView")
        }
        assertEquals(3, engineMethods.size)
        for (method in engineMethods) {
            assertEquals(method.name, KVisibility.INTERNAL, method.visibility)
        }

        // Check Java methods too: a generated accessor must not become a second raw reader.
        val rawReturningMethods = GameInfo::class.java.declaredMethods.filter {
            !Modifier.isPrivate(it.modifiers) && containsRawRecordType(it.genericReturnType.typeName)
        }
        assertTrue(rawReturningMethods.isEmpty())
    }

    @Test
    fun `attack view exposes only a bound read-only query and cannot be unwrapped`() {
        assertTrue(Modifier.isFinal(AttackEventsView::class.java.modifiers))
        assertFalse(View::class.java.isAssignableFrom(AttackEventsView::class.java))
        assertTrue(AttackEventsView::class.constructors.all { it.visibility == KVisibility.INTERNAL })

        val publicFunctions = AttackEventsView::class.declaredMemberFunctions.filter {
            it.visibility == KVisibility.PUBLIC
        }
        assertEquals(listOf("getObservedAttacks"), publicFunctions.map { it.name })
        val query = publicFunctions.single()
        assertEquals(List::class, query.returnType.classifier)
        assertEquals(ObservedAttack::class, query.returnType.arguments.single().type!!.classifier)
        val argument = query.parameters.single { it.kind == KParameter.Kind.VALUE }
        assertEquals(MapUnitView::class, argument.type.classifier)
        assertTrue(argument.type.isMarkedNullable)
        assertTrue(argument.isOptional)

        val publicProperties = AttackEventsView::class.declaredMemberProperties.filter {
            it.visibility == KVisibility.PUBLIC
        }
        assertEquals(listOf("spectatorMode"), publicProperties.map { it.name })
        assertEquals(Boolean::class, publicProperties.single().returnType.classifier)
        assertTrue(publicProperties.none { it is KMutableProperty<*> })
        assertTrue(AttackEventsView::class.java.declaredMethods.none {
            !Modifier.isPrivate(it.modifiers) && containsRawRecordType(it.toGenericString())
        })
        assertTrue(AttackEventsView::class.java.declaredFields.all { Modifier.isPrivate(it.modifiers) })
    }

    @Test
    fun `observations contain only immutable permitted values`() {
        assertTrue(Modifier.isFinal(ObservedAttack::class.java.modifiers))
        val properties = ObservedAttack::class.declaredMemberProperties
        assertEquals(setOf("turn", "source", "target"), properties.map { it.name }.toSet())
        assertTrue(properties.none { it is KMutableProperty<*> })
        for (property in properties) {
            assertEquals(if (property.name == "turn") Int::class else HexCoord::class,
                property.returnType.classifier)
            assertEquals(property.name != "turn", property.returnType.isMarkedNullable)
        }
        assertTrue(HexCoord::class.declaredMemberProperties.none { it is KMutableProperty<*> })
    }

    @Test
    fun `attack recorder has engine-only construction and bookkeeping`() {
        assertTrue(Modifier.isFinal(AttackRecorder::class.java.modifiers))
        assertFalse(IsPartOfGameInfoSerialization::class.java.isAssignableFrom(AttackRecorder::class.java))
        assertFalse(AutoCloseable::class.java.isAssignableFrom(AttackRecorder::class.java))
        assertTrue(AttackRecorder::class.declaredMemberFunctions.none { it.name in setOf("close", "finalize") })
        assertTrue(AttackRecorder::class.constructors.all { it.visibility == KVisibility.INTERNAL })
        assertTrue(AttackRecorder::class.declaredMemberFunctions.none { it.visibility == KVisibility.PUBLIC })
        assertTrue(AttackRecorder::class.java.declaredFields.all { Modifier.isPrivate(it.modifiers) })
    }

    @Test
    fun `attack recorders stay out of views cached game contexts and serialized state`() {
        val violations = ArrayList<String>()
        val classes = coreClassFiles().map(::ClassReader).toList()
        val parentTypes = classes.associate { it.className to (listOfNotNull(it.superName) + it.interfaces) }
        fun isSerialized(className: String): Boolean = className == serializedStateInterface ||
            parentTypes[className].orEmpty().any(::isSerialized)
        fun containsRecorder(type: String?): Boolean = type?.let(attackRecorderType::containsMatchIn) == true

        for (reader in classes) {
            val callerClass = reader.className
            val isPresentation = callerClass.startsWith("com/unciv/view/") || callerClass.startsWith("com/unciv/ui/")
            val isRecorder = callerClass == attackRecorderClass || callerClass.startsWith("$attackRecorderClass$")
            val isCachedContext = callerClass == cachedGameContextClass || callerClass.startsWith("$cachedGameContextClass$")
            reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                fun checkThreadLocal(type: String?) {
                    if (isRecorder && type != null && (type.contains("java/lang/ThreadLocal")
                            || type.contains("java/lang/InheritableThreadLocal")))
                        violations.add("$callerClass uses ambient thread-local recording")
                }

                override fun visitField(access: Int, name: String, descriptor: String,
                                        signature: String?, value: Any?): FieldVisitor? {
                    checkThreadLocal(descriptor)
                    checkThreadLocal(signature)
                    if ((containsRecorder(descriptor) || containsRecorder(signature))
                        && (isPresentation || isSerialized(callerClass) || isCachedContext
                            || access and Opcodes.ACC_STATIC != 0))
                        violations.add("$callerClass.$name retains an attack recorder")
                    return null
                }

                override fun visitMethod(access: Int, name: String, descriptor: String,
                                         signature: String?, exceptions: Array<out String>?): MethodVisitor {
                    val caller = "$callerClass.$name"
                    fun checkType(type: String?) {
                        checkThreadLocal(type)
                        if (isPresentation && containsRecorder(type))
                            violations.add("$caller exposes or accesses an attack recorder")
                    }
                    fun checkCall(owner: String, calledDescriptor: String) {
                        checkType(owner)
                        checkThreadLocal(calledDescriptor)
                        // Ordinary UI calls may invoke mutation methods with a default null
                        // recorder argument in the JVM descriptor. A returned recorder is different.
                        checkType(if (calledDescriptor.startsWith("("))
                            Type.getReturnType(calledDescriptor).descriptor else calledDescriptor)
                    }
                    checkType(descriptor)
                    checkType(signature)
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(opcode: Int, owner: String, name: String,
                                                     descriptor: String, isInterface: Boolean) {
                            checkCall(owner, descriptor)
                        }

                        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                            checkType(owner)
                            checkType(descriptor)
                        }

                        override fun visitTypeInsn(opcode: Int, type: String) = checkType(type)

                        override fun visitLdcInsn(value: Any) {
                            if (value is Handle) checkCall(value.owner, value.desc)
                            else if (value is Type) checkType(value.descriptor)
                        }

                        override fun visitInvokeDynamicInsn(name: String, descriptor: String,
                                                            bootstrapMethodHandle: Handle,
                                                            vararg bootstrapMethodArguments: Any) {
                            checkType(descriptor)
                            for (argument in bootstrapMethodArguments) {
                                if (argument is Handle) checkCall(argument.owner, argument.desc)
                                else if (argument is Type) checkType(argument.descriptor)
                            }
                        }
                    }
                }
            }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        assertTrue(classes.any { it.className == attackRecorderClass })
        assertFalse(classes.any { it.className == "${battlePackage}BattleDamageRecorder" })
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `compiled production callers respect the trusted history boundary`() {
        val violations = ArrayList<String>()
        val seenOperations = HashSet<String>()
        val seenClasses = HashSet<String>()
        val restrictedOperations = restrictedJvmOperations()
        for (bytecode in coreClassFiles()) {
            val reader = ClassReader(bytecode)
            val callerClass = reader.className
            seenClasses.add(callerClass)
            reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitField(access: Int, name: String, descriptor: String,
                                        signature: String?, value: Any?): FieldVisitor? {
                    if (!isTrustedRecordConsumer(callerClass)
                        && (containsRawRecordType(descriptor) || signature?.let(::containsRawRecordType) == true))
                        violations.add("$callerClass.$name stores privileged records")
                    return null
                }

                override fun visitMethod(access: Int, name: String, descriptor: String,
                                         signature: String?, exceptions: Array<out String>?): MethodVisitor {
                    val caller = "$callerClass.${name.substringBefore('$')}"
                    fun checkRawType(type: String?) {
                        if (!isTrustedRecordConsumer(callerClass) && type != null && containsRawRecordType(type))
                            violations.add("$caller refers to privileged record type $type")
                    }
                    fun checkCall(owner: String, calledName: String, calledDescriptor: String) {
                        checkRawType(owner)
                        checkRawType(calledDescriptor)
                        val operation = restrictedOperations["$owner.$calledName$calledDescriptor"] ?: return
                        val allowedCallers = trustedOperationCallers.getValue(operation)
                        seenOperations.add(operation)
                        if (caller !in allowedCallers)
                            violations.add("$caller calls restricted $operation")
                    }
                    checkRawType(descriptor)
                    checkRawType(signature)
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(opcode: Int, owner: String, name: String,
                                                     descriptor: String, isInterface: Boolean) {
                            checkCall(owner, name, descriptor)
                        }

                        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                            checkRawType(owner)
                            checkRawType(descriptor)
                            val allowedCallers = trustedStorageCallers["$owner.$name"] ?: return
                            if (caller !in allowedCallers)
                                violations.add("$caller accesses private history storage $owner.$name")
                        }

                        override fun visitTypeInsn(opcode: Int, type: String) = checkRawType(type)

                        override fun visitLdcInsn(value: Any) {
                            if (value is Handle) checkCall(value.owner, value.name, value.desc)
                            else if (value is Type) checkRawType(value.descriptor)
                        }

                        override fun visitInvokeDynamicInsn(name: String, descriptor: String,
                                                            bootstrapMethodHandle: Handle,
                                                            vararg bootstrapMethodArguments: Any) {
                            checkRawType(descriptor)
                            for (argument in bootstrapMethodArguments) {
                                if (argument is Handle) checkCall(argument.owner, argument.name, argument.desc)
                                else checkRawType(argument.toString())
                            }
                        }
                    }
                }
            }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        // Fail if the test is accidentally scanning an empty or unrelated class directory.
        assertTrue(seenClasses.containsAll(listOf(gameInfoClass, attackViewClass, gameViewClass)))
        assertEquals(trustedOperationCallers.keys, seenOperations)
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    private fun containsRawRecordType(type: String): Boolean = rawRecordTypes.any { it.containsMatchIn(type) }

    private fun isTrustedRecordConsumer(className: String): Boolean =
        className == gameInfoClass || className == attackViewClass || className.startsWith(battlePackage)

    /**
     * Resolve the declared Kotlin operations to exact JVM names and descriptors. Stripping '$'
     * suffixes from callees would confuse an internal method's module name with generated helpers
     * such as expireAttackEventsFor$lambda$0, which is only its removal predicate.
     */
    private fun restrictedJvmOperations(): Map<String, String> = buildMap {
        for ((className, type) in listOf(gameInfoClass to GameInfo::class, attackRecorderClass to AttackRecorder::class)) {
            for (function in type.declaredMemberFunctions) {
                val operation = "$className.${function.name}"
                if (operation !in trustedOperationCallers) continue
                val method = function.javaMethod!!
                put("$className.${method.name}${Type.getMethodDescriptor(method)}", operation)
            }
        }
        val constructor = AttackEventsView::class.java.declaredConstructors.single()
        put("$attackViewClass.<init>${Type.getConstructorDescriptor(constructor)}", "$attackViewClass.<init>")
    }

    private fun coreClassFiles(): Sequence<ByteArray> = sequence {
        val location = File(GameInfo::class.java.protectionDomain.codeSource.location.toURI())
        if (location.isDirectory) {
            for (file in location.walkTopDown().filter { it.isFile && it.extension == "class" })
                yield(file.readBytes())
        } else {
            JarFile(location).use { jar ->
                for (entry in jar.entries().asSequence().filter { it.name.endsWith(".class") })
                    yield(jar.getInputStream(entry).use { it.readBytes() })
            }
        }
    }

    companion object {
        private val rawRecordTypes = listOf(AttackEvent::class.java, AttackParticipant::class.java, AttackInterception::class.java)
            .flatMap { listOf(it.name, it.name.replace('.', '/')) }
            .map { Regex("${Regex.escape(it)}(?![A-Za-z0-9_])") }
        private const val gameInfoClass = "com/unciv/logic/GameInfo"
        private const val attackViewClass = "com/unciv/view/AttackEventsView"
        private const val gameViewClass = "com/unciv/view/GameView"
        private const val battlePackage = "com/unciv/logic/battle/"
        private const val attackRecorderClass = "${battlePackage}AttackRecorder"
        private const val cachedGameContextClass = "com/unciv/models/ruleset/unique/GameContext"
        private const val serializedStateInterface = "com/unciv/logic/IsPartOfGameInfoSerialization"
        private val attackRecorderType = Regex("${Regex.escape(attackRecorderClass)}(?![A-Za-z0-9_])")
        private val combatEntryPoints = setOf("${battlePackage}Battle.attack", "${battlePackage}Nuke.NUKE",
            "${battlePackage}AirInterception.airSweep")
        private val trustedOperationCallers = mapOf(
            "$gameInfoClass.storeAttack" to combatEntryPoints,
            "$attackRecorderClass.finish" to combatEntryPoints,
            "$attackRecorderClass.finishIncomplete" to combatEntryPoints,
            "$gameInfoClass.expireAttackEventsFor" to setOf("com/unciv/logic/civilization/managers/TurnManager.startTurn"),
            "$gameInfoClass.createAttackEventsView" to setOf("$gameViewClass.<init>"),
            "$attackViewClass.<init>" to setOf("$gameInfoClass.createAttackEventsView"),
        )
        private val trustedStorageCallers = mapOf(
            "$gameInfoClass.attackEvents" to setOf("<init>", "clone", "storeAttack", "expireAttackEventsFor",
                "createAttackEventsView").map { "$gameInfoClass.$it" }.toSet(),
            "$attackViewClass.events" to setOf("$attackViewClass.<init>", "$attackViewClass.getObservedAttacks"),
        )
    }
}
