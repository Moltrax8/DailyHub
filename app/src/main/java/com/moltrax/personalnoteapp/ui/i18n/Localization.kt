package com.moltrax.personalnoteapp.ui.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Languages supported by the app. [code] is both the value stored in DataStore and the Android
 * resource qualifier (values/ = tr default, values-en/ = en).
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    TURKISH("tr", "Türkçe"),
    ENGLISH("en", "English"),
}

/**
 * Returns a [ContextWrapper] wrapping this context with resources localized for the given [code]
 * language. IMPORTANT: the base context (usually the Activity) is preserved — only [getResources]
 * is overridden. This way mechanisms walking the Activity chain like `hiltViewModel()` keep working,
 * while `stringResource` / `context.getString` returns the localized text.
 */
fun Context.localizedFor(code: String): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(Locale(code))
    val localizedResources = createConfigurationContext(config).resources
    return object : ContextWrapper(this) {
        override fun getResources(): Resources = localizedResources
    }
}

/** Localized config to trigger [stringResource] recomposition. */
fun localizedConfiguration(base: Configuration, code: String): Configuration =
    Configuration(base).apply { setLocale(Locale(code)) }
