package com.aiyu.ridelink.core.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

data class AppLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String,
)

/**
 * Persists and applies the in-app language override across API 26–32 (Context/Resources wrapping)
 * and API 33+ (platform [LocaleManager]).
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY_TAG = "language_tag"

    val SUPPORTED_LANGUAGES: List<AppLanguage> = listOf(
        AppLanguage("en", "English", "English"),
        AppLanguage("hi", "हिन्दी", "Hindi"),
        AppLanguage("bn", "বাংলা", "Bengali"),
        AppLanguage("te", "తెలుగు", "Telugu"),
        AppLanguage("mr", "मराठी", "Marathi"),
        AppLanguage("ta", "தமிழ்", "Tamil"),
        AppLanguage("ur", "اردو", "Urdu"),
        AppLanguage("gu", "ગુજરાતી", "Gujarati"),
        AppLanguage("kn", "ಕನ್ನಡ", "Kannada"),
        AppLanguage("ml", "മലയാളം", "Malayalam"),
        AppLanguage("or", "ଓଡ଼ିଆ", "Odia"),
        AppLanguage("pa", "ਪੰਜਾਬੀ", "Punjabi"),
        AppLanguage("as", "অসমীয়া", "Assamese"),
        AppLanguage("ks", "کٲشُر", "Kashmiri"),
        AppLanguage("ne", "नेपाली", "Nepali"),
        AppLanguage("sd", "سنڌي", "Sindhi"),
        AppLanguage("sa", "संस्कृतम्", "Sanskrit"),
        AppLanguage("mai", "मैथिली", "Maithili"),
        AppLanguage("sat", "ᱥᱟᱱᱛᱟᱲᱤ", "Santali"),
        AppLanguage("kok", "कोंकणी", "Konkani"),
        AppLanguage("doi", "डोगरी", "Dogri"),
        AppLanguage("mni", "মৈতৈলোন্", "Manipuri"),
        AppLanguage("brx", "बड़ो", "Bodo"),
    )

    fun findLanguage(tag: String?): AppLanguage? {
        val clean = tag?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return SUPPORTED_LANGUAGES.firstOrNull { it.tag.equals(clean, ignoreCase = true) }
            ?: SUPPORTED_LANGUAGES.firstOrNull { clean.startsWith("${it.tag}-", ignoreCase = true) }
    }

    fun getTag(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales == null || locales.isEmpty) return null
            return locales[0]?.toLanguageTag()
        }
        return prefs(context).getString(KEY_TAG, null)?.takeIf { it.isNotBlank() }
    }

    fun apply(context: Context, tag: String?) {
        val clean = tag?.trim()?.takeIf { it.isNotEmpty() }
        prefs(context).edit().apply {
            if (clean == null) remove(KEY_TAG) else putString(KEY_TAG, clean)
        }.commit()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java)
            lm?.applicationLocales = if (clean == null) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(clean)
            }
        } else {
            applyToResources(context.applicationContext)
        }
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val clean = prefs(base).getString(KEY_TAG, null)?.takeIf { it.isNotBlank() }
        val locale = if (clean != null) Locale.forLanguageTag(clean) else systemDefaultLocale()
        Locale.setDefault(locale)
        if (clean == null) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun applyToResources(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val clean = prefs(context).getString(KEY_TAG, null)?.takeIf { it.isNotBlank() }
        val locale = if (clean != null) Locale.forLanguageTag(clean) else systemDefaultLocale()
        Locale.setDefault(locale)
        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
    }

    private fun systemDefaultLocale(): Locale =
        Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
