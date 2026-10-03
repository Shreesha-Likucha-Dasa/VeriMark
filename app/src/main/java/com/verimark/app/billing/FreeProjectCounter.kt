package com.verimark.app.billing

import android.content.Context
import androidx.core.content.edit

/**
 * Persistent lifetime counter of free-tier project creations.
 *
 * The count only ever increments and survives app restarts, process death, and
 * device reboots. It is never decremented — deleting a project does not refund
 * a consumed creation, and importing/exporting/sharing never changes it.
 */
object FreeProjectCounter {

    private const val PREFS = "verimark_free_limit"
    private const val KEY_USED = "free_project_creations_used"

    fun used(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_USED, 0)

    /** Records one more successful creation and returns the new count. */
    fun increment(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getInt(KEY_USED, 0) + 1
        prefs.edit { putInt(KEY_USED, next) }
        return next
    }
}
