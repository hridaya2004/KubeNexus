package dev.hridaya.kubenexus.presentation.common.share

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

/**
 * Copies text to the system clipboard.
 *
 * Since Android 13 the system shows a preview of copied text. Logs, terminal output, error
 * traces and object metadata can contain credentials or personal data, so those copies are
 * flagged with [ClipDescription.EXTRA_IS_SENSITIVE], which hides the preview.
 */
object ClipboardHelper {

    fun copy(context: Context, label: String, text: String, sensitive: Boolean) {
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
    }
}
