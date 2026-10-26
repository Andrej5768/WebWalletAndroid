package org.andrewdev.webwallet.ui

import org.andrewdev.webwallet.core.common.UiLanguage
import org.andrewdev.webwallet.core.datastore.LanguageSetting
import java.util.Locale

/**
 * The UI language right now (docs/01 §6): the setting when fixed, else the system language when it is one of ours,
 * else Ukrainian. Read by the network layer for `Accept-Language` and by the root for the resources.
 */
fun interface LanguageResolver {
    fun setting(): LanguageSetting

    fun current(): UiLanguage = resolve(setting())

    companion object {
        fun resolve(setting: LanguageSetting, systemLocale: Locale = Locale.getDefault()): UiLanguage = when (setting) {
            LanguageSetting.UK -> UiLanguage.UK
            LanguageSetting.EN -> UiLanguage.EN
            LanguageSetting.SYSTEM -> if (systemLocale.language == "en") UiLanguage.EN else UiLanguage.UK
        }
    }
}
