package com.hiba.simplephotoframe

import android.content.Context
import android.content.res.Configuration
import java.util.*

object LocaleHelper {
    fun setLocale(context: Context, language: String): Context {
        if (language == "System") return context

        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }

    fun applyLocale(context: Context) {
        val settings = SettingsManager(context).getSettings()
        if (settings.language != "System") {
            val locale = Locale(settings.language)
            Locale.setDefault(locale)
            val config = context.resources.configuration
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }
}
