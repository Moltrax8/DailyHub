package com.moltrax.personalnoteapp.ui.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Uygulamanın desteklediği diller. [code] hem DataStore'da saklanan değer hem de Android kaynak
 * niteleyicisidir (values/ = tr varsayılan, values-en/ = en).
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    TURKISH("tr", "Türkçe"),
    ENGLISH("en", "English"),
}

/**
 * Bu context'i verilen [code] diline göre yerelleştirilmiş kaynaklarla saran bir [ContextWrapper]
 * döndürür. ÖNEMLİ: taban context (genellikle Activity) korunur — yalnızca [getResources] override
 * edilir. Böylece `hiltViewModel()` gibi Activity zincirini yürüyen mekanizmalar bozulmaz, ama
 * `stringResource` / `context.getString` yerelleştirilmiş metni döndürür.
 */
fun Context.localizedFor(code: String): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(Locale(code))
    val localizedResources = createConfigurationContext(config).resources
    return object : ContextWrapper(this) {
        override fun getResources(): Resources = localizedResources
    }
}

/** [stringResource]'un yeniden derlenmesini (recomposition) tetiklemek için yerelleştirilmiş config. */
fun localizedConfiguration(base: Configuration, code: String): Configuration =
    Configuration(base).apply { setLocale(Locale(code)) }
