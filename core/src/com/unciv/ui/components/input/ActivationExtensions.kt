package com.unciv.ui.components.input

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.Disableable
import com.unciv.models.UncivSound
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip

/** Used to stop activation events if this returns `true`. */
internal fun Actor.isActive(): Boolean = isVisible && ((this as? Disableable)?.isDisabled != true)

/** Routes events from the listener to [ActorAttachments] */
internal fun Actor.activate(type: ActivationTypes = ActivationTypes.Tap): Boolean {
    if (!isActive()) return false
    val attachment = ActorAttachments.getOrNull(this) ?: return false
    return attachment.activate(type)
}

/** Accesses the [shortcut dispatcher][ActorKeyShortcutDispatcher] for your actor
 *  (creates one if the actor has none).
 *
 *  Note that shortcuts you add with handlers are routed directly, those without are routed to [onActivation] with type [ActivationTypes.Keystroke]. */
val Actor.keyShortcuts
    get() = ActorAttachments.get(this).keyShortcuts

/** Routes input events of type [type] to your handler [action].
 *  Will also be activated for events [equivalent][ActivationTypes.isEquivalent] to [type] unless [noEquivalence] is `true`.
 *  A [sound] will be played (concurrently) on activation unless you specify [UncivSound.Silent].
 *  @return `this` to allow chaining
 */
fun Actor.onActivation(
    type: ActivationTypes,
    sound: UncivSound = UncivSound.Click,
    noEquivalence: Boolean = false,
    action: ActivationAction
): Actor {
    ActorAttachments.get(this).addActivationAction(type, sound, noEquivalence, action)
    return this
}

/** Assigns an activation [handler][action] to your Widget, which reacts to clicks and a [key stroke][binding].
 *  A tooltip is attached automatically, if there is a keyboard and the [binding] has a mapping.
 *  A [sound] will be played (concurrently) on activation unless you specify [UncivSound.Silent].
 *  @return `this` to allow chaining
 */
fun Actor.onActivation(sound: UncivSound = UncivSound.Click, binding: KeyboardBinding, action: ActivationAction): Actor {
    onActivation(ActivationTypes.Tap, sound, action = action)
    keyShortcuts.add(binding)
    addTooltip(binding)
    return this
}

/** Routes clicks and [keyboard shortcuts][keyShortcuts] to your handler [action].
 *  A [sound] will be played (concurrently) on activation unless you specify [UncivSound.Silent].
 *  @return `this` to allow chaining
 */
fun Actor.onActivation(sound: UncivSound = UncivSound.Click, action: ActivationAction): Actor =
    onActivation(ActivationTypes.Tap, sound, action = action)

/** Routes clicks and [keyboard shortcuts][keyShortcuts] to your handler [action].
 *  A [Click sound][UncivSound.Click] will be played (concurrently).
 *  @return `this` to allow chaining
 */
fun Actor.onActivation(action: ActivationAction): Actor =
    onActivation(ActivationTypes.Tap, action = action)

/** Routes clicks to your handler [action], ignoring [keyboard shortcuts][keyShortcuts].
 *  A [sound] will be played (concurrently) on activation unless you specify [UncivSound.Silent].
 *  @return `this` to allow chaining
 */
fun Actor.onClick(sound: UncivSound = UncivSound.Click, action: ActivationAction): Actor =
    onActivation(ActivationTypes.Tap, sound, noEquivalence = true, action)

/** Routes clicks to your handler [action], ignoring [keyboard shortcuts][keyShortcuts].
 *  A [Click sound][UncivSound.Click] will be played (concurrently).
 *  @return `this` to allow chaining
 */
fun Actor.onClick(action: ActivationAction): Actor =
    onActivation(ActivationTypes.Tap, noEquivalence = true, action = action)

/** Routes double-clicks to your handler [action].
 *  A [sound] will be played (concurrently) on activation unless you specify [UncivSound.Silent].
 *  @return `this` to allow chaining
 */
fun Actor.onDoubleClick(sound: UncivSound = UncivSound.Click, action: ActivationAction): Actor =
    onActivation(ActivationTypes.Doubletap, sound, action = action)

/** Routes right-clicks and long-presses to your handler [action].
 *  These are treated as equivalent so both desktop and mobile can access the same functionality with methods common to the platform.
 *  A [sound] will be played (concurrently) on activation unless you specify [UncivSound.Silent].
 *  @return `this` to allow chaining
 */
fun Actor.onRightClick(sound: UncivSound = UncivSound.Click, action: ActivationAction): Actor =
    onActivation(ActivationTypes.RightClick, sound, action = action)

/** Routes long-presses (but not right-clicks) to your handler [action].
 *  A [sound] will be played (concurrently) on activation unless you specify [UncivSound.Silent].
 *  @return `this` to allow chaining
 */
fun Actor.onLongPress(sound: UncivSound = UncivSound.Click, action: ActivationAction): Actor =
    onActivation(ActivationTypes.Longpress, sound, noEquivalence = true, action)

/** Clears activation actions for a specific [type], and, if [noEquivalence] is `true`,
 *  its [equivalent][ActivationTypes.isEquivalent] types.
 */
fun Actor.clearActivationActions(type: ActivationTypes, noEquivalence: Boolean = true) {
    ActorAttachments.get(this).clearActivationActions(type, noEquivalence)
}

/**
 * Install shortcut dispatcher for this stage. It activates all actions associated with the
 * pressed key in [additionalShortcuts] (if specified) and **all** actors in the stage - recursively.
 *
 * It is possible to temporarily disable or veto some shortcut dispatchers by passing an appropriate
 * [dispatcherVetoerCreator] function. This function may return a [DispatcherVetoer], which
 * will then be used to evaluate all shortcut sources in the stage. This two-step vetoing
 * mechanism allows the callback ([dispatcherVetoerCreator]) to perform expensive preparations
 * only once per keypress (doing them in the returned [DispatcherVetoer] would instead be once
 * per keypress/actor combination).
 *
 * Note - screens containing a TileGroupMap **should** supply a vetoer skipping that actor, or else
 * the scanning Deque will be several thousand entries deep.
 */
fun Stage.installShortcutDispatcher(additionalShortcuts: KeyShortcutDispatcher? = null, dispatcherVetoerCreator: () -> DispatcherVetoer?) {
    addListener(KeyShortcutListener(actors.asSequence(), additionalShortcuts, dispatcherVetoerCreator))
}

private class OnChangeListener(val function: (event: ChangeEvent?) -> Unit) : ChangeListener() {
    override fun changed(event: ChangeEvent?, actor: Actor?) {
        function(event)
    }
}

/** Attach a ChangeListener to [this] and route its changed event to [action] */
fun Actor.onChange(action: (event: ChangeListener.ChangeEvent?) -> Unit): Actor {
    this.addListener(OnChangeListener(action))
    return this
}
