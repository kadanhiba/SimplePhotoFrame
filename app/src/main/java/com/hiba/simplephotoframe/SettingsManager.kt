package com.hiba.simplephotoframe

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SlideshowSettings(
    var durationSeconds: Int = 5,
    var transition: String = "Fade",
    var transitionDurationSeconds: Float = 0.5f,
    var showClock: Boolean = false,
    var dateDisplay: String = "None",
    var preventBurnIn: Boolean = false,
    var clockLocation: String = "Top-Right",
    var autoMuteVideo: Boolean = false,
    var keepScreenOn: Boolean = true,
    var useSchedule: Boolean = false,
    var startTime: String = "08:00",
    var endTime: String = "22:00",
    var order: String = "randomized",
    var developerMode: Boolean = false,
    var language: String = "System"
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("slideshow_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getSettings(): SlideshowSettings {
        val json = prefs.getString("settings", null)
        return if (json != null) {
            gson.fromJson(json, SlideshowSettings::class.java)
        } else {
            SlideshowSettings()
        }
    }

    fun saveSettings(settings: SlideshowSettings) {
        prefs.edit().putString("settings", gson.toJson(settings)).apply()
    }

    fun getFolders(): MutableList<String> {
        val json = prefs.getString("folders", null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<String>>() {}.type
            gson.fromJson(json, type)
        } else {
            mutableListOf()
        }
    }

    fun saveFolders(folders: List<String>) {
        prefs.edit().putString("folders", gson.toJson(folders)).apply()
    }
}
