package com.kursi.core.prefs

import com.russhwolf.settings.Settings

class ApiKeyStore(
    private val settings: Settings = Settings(),
) {
    fun getAnthropicKey(): String? = settings.getString(KEY_ANTHROPIC, "").ifBlank { null }

    fun setAnthropicKey(key: String?) = setOrRemove(KEY_ANTHROPIC, key)

    fun getOpenAiKey(): String? = settings.getString(KEY_OPENAI, "").ifBlank { null }

    fun setOpenAiKey(key: String?) = setOrRemove(KEY_OPENAI, key)

    fun getGeminiKey(): String? = settings.getString(KEY_GEMINI, "").ifBlank { null }

    fun setGeminiKey(key: String?) = setOrRemove(KEY_GEMINI, key)

    fun getSelectedProvider(): String = settings.getString(KEY_PROVIDER, DEFAULT_PROVIDER)

    fun setSelectedProvider(id: String) {
        settings.putString(KEY_PROVIDER, id)
    }

    fun getUseOnDevice(): Boolean = settings.getBoolean(KEY_USE_ON_DEVICE, false)

    fun setUseOnDevice(enabled: Boolean) {
        settings.putBoolean(KEY_USE_ON_DEVICE, enabled)
    }

    fun clearAll() {
        settings.remove(KEY_ANTHROPIC)
        settings.remove(KEY_OPENAI)
        settings.remove(KEY_GEMINI)
        settings.remove(KEY_PROVIDER)
        settings.remove(KEY_USE_ON_DEVICE)
    }

    private fun setOrRemove(
        key: String,
        value: String?,
    ) {
        if (value.isNullOrBlank()) {
            settings.remove(key)
        } else {
            settings.putString(key, value)
        }
    }

    companion object {
        private const val KEY_ANTHROPIC = "ai_key_anthropic"
        private const val KEY_OPENAI = "ai_key_openai"
        private const val KEY_GEMINI = "ai_key_gemini"
        private const val KEY_PROVIDER = "ai_selected_provider"
        private const val KEY_USE_ON_DEVICE = "ai_use_on_device"
        private const val DEFAULT_PROVIDER = "ismcts_only"
    }
}
