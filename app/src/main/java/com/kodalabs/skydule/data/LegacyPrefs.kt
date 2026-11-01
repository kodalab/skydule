package com.kodalabs.skydule.data

import android.content.Context

/** Preference files that were renamed when the app dropped its old working name. */
internal val RENAMED_PREFS = listOf(
    "gradule_reminders" to "skydule_reminders",
    "gradule_day_icon" to "skydule_day_icon",
    "gradule_diag" to "skydule_diag",
    "gradule_places" to "skydule_places",
)

/** Entries of [old] that should be copied: only when the new file has none of them yet (never overwrite newer data). */
internal fun entriesToCopy(old: Map<String, *>, new: Map<String, *>): Map<String, Any?> =
    if (new.isNotEmpty()) emptyMap() else old.filterKeys { it !in new }

/** Copies each old preference file into its new name once, then clears the old one. Safe to run on every start. */
object LegacyPrefs {
    fun migrate(context: Context) {
        for ((oldName, newName) in RENAMED_PREFS) {
            try {
                val old = context.getSharedPreferences(oldName, Context.MODE_PRIVATE)
                if (old.all.isEmpty()) continue
                val target = context.getSharedPreferences(newName, Context.MODE_PRIVATE)
                val copy = entriesToCopy(old.all, target.all)
                if (copy.isNotEmpty()) {
                    val e = target.edit()
                    for ((k, v) in copy) when (v) {
                        is String -> e.putString(k, v)
                        is Int -> e.putInt(k, v)
                        is Long -> e.putLong(k, v)
                        is Boolean -> e.putBoolean(k, v)
                        is Float -> e.putFloat(k, v)
                        is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
                    }
                    if (!e.commit()) continue
                }
                old.edit().clear().commit()
            } catch (_: Exception) {
                // A failed copy just means the old state is lost; never block startup.
            }
        }
    }
}
