package dev.hridaya.kubenexus.presentation.pods.components.terminal

import android.view.KeyEvent

/**
 * The on-screen CTRL and ALT keys are one-shot modifiers, like sticky keys: they apply to the
 * next key or character and then release.
 */
internal data class TerminalModifiers(val ctrl: Boolean = false, val alt: Boolean = false) {
    val any: Boolean get() = ctrl || alt

    /** Android meta state for the key encoder. */
    val metaState: Int
        get() = (if (ctrl) KeyEvent.META_CTRL_ON else 0) or (if (alt) KeyEvent.META_ALT_ON else 0)

    /**
     * The bytes a terminal sends for [char] with these modifiers: CTRL maps it to its control
     * code (Ctrl+C is 0x03, Ctrl+[ is ESC), ALT prefixes ESC.
     */
    fun apply(char: Char): String {
        val base = if (ctrl) controlCode(char)?.toString() ?: char.toString() else char.toString()
        return if (alt) "\u001B$base" else base
    }

    companion object {
        fun controlCode(char: Char): Char? = when (val upper = char.uppercaseChar()) {
            in '@'..'_' -> (upper.code - 0x40).toChar()
            ' ' -> '\u0000'
            '?' -> '\u007F'
            else -> null
        }
    }
}

/**
 * Returns the single character that [new] appends to [old], or null if the edit was anything
 * else (a deletion, a paste, a change in the middle).
 */
internal fun appendedChar(old: String, new: String): Char? =
    if (new.length == old.length + 1 && new.startsWith(old)) new.last() else null
