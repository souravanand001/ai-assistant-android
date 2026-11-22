package com.app.assistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.assistant.BuildConfig
import com.app.assistant.llm.CustomServerPreset
import com.app.assistant.llm.DetectedServerConfig
import com.app.assistant.llm.LlmConfig
import com.app.assistant.llm.LlmProvider
import com.app.assistant.llm.ModelCapabilities
import com.app.assistant.llm.ModelCapabilityProber
import com.app.assistant.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers
import java.io.File
import android.util.Log
import java.io.IOException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import java.lang.reflect.Modifier
import android.content.Context
import android.app.role.RoleManager
import android.content.ComponentName
import android.provider.Settings
import android.os.Build

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val currentBytes: Long, val totalBytes: Long) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

sealed class VerificationState {
    object Idle : VerificationState()
    object Verifying : VerificationState()
    data class Success(val capabilities: ModelCapabilities) : VerificationState()
    data class Error(val message: String) : VerificationState()
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient,
    private val customConfigHttpClient: OkHttpClient = okHttpClient
) : ViewModel() {

    private val modelManager = com.app.assistant.speech.SpeechModelManager(settingsRepository.context)
    private val ttsModelManager = com.app.assistant.tts.TtsModelManager(settingsRepository.context)

    private val _modelDownloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val modelDownloadState: StateFlow<DownloadState> = _modelDownloadState.asStateFlow()

    private val _isModelInstalled = MutableStateFlow(modelManager.isModelDownloaded())

    private val _isTranslationEnabled = MutableStateFlow(settingsRepository.getIsTranslationEnabled())
    val isTranslationEnabled: StateFlow<Boolean> = _isTranslationEnabled.asStateFlow()

    private val _activeLanguageCode = MutableStateFlow(settingsRepository.getActiveLanguageCode())
    val activeLanguageCode: StateFlow<String> = _activeLanguageCode.asStateFlow()

    private val _downloadedTranslationLanguages = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTranslationLanguages: StateFlow<Set<String>> = _downloadedTranslationLanguages.asStateFlow()

    private val _downloadingTranslationLanguages = MutableStateFlow<Set<String>>(emptySet())
    val downloadingTranslationLanguages: StateFlow<Set<String>> = _downloadingTranslationLanguages.asStateFlow()

    var translationLanguages: List<Pair<String, String>> = emptyList()
        private set

    private val _isDefaultAssistant = MutableStateFlow(false)
    val isDefaultAssistant: StateFlow<Boolean> = _isDefaultAssistant.asStateFlow()

    init {
        translationLanguages = getPublicStaticFinalStringsWithNames(TranslateLanguage::class.java)
        refreshDownloadedTranslationModels()
        checkDefaultAssistantStatus()
    }

    fun checkDefaultAssistantStatus() {
        val context = settingsRepository.context
        val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        } else {
            val assistantSetting = Settings.Secure.getString(context.contentResolver, "assistant")
            if (assistantSetting.isNullOrEmpty()) {
                false
            } else {
                val currentAssistant = ComponentName.unflattenFromString(assistantSetting)
                val myAssistant = ComponentName(context, com.app.assistant.AssistActivity::class.java)
                currentAssistant == myAssistant
            }
        }
        _isDefaultAssistant.value = isDefault
    }

    val isModelInstalled: StateFlow<Boolean> = _isModelInstalled.asStateFlow()

    private val _ttsModelDownloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val ttsModelDownloadState: StateFlow<DownloadState> = _ttsModelDownloadState.asStateFlow()

    private val _isTtsModelInstalled = MutableStateFlow(ttsModelManager.isModelDownloaded())
    val isTtsModelInstalled: StateFlow<Boolean> = _isTtsModelInstalled.asStateFlow()

    private val _verificationState = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val verificationState: StateFlow<VerificationState> = _verificationState.asStateFlow()

    private val _fetchedModels = MutableStateFlow<Map<LlmProvider, List<String>>>(emptyMap())
    val fetchedModels: StateFlow<Map<LlmProvider, List<String>>> = _fetchedModels.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private val _modelFetchError = MutableStateFlow<String?>(null)
    val modelFetchError: StateFlow<String?> = _modelFetchError.asStateFlow()

    private val _isAutoDetecting = MutableStateFlow(false)
    val isAutoDetecting: StateFlow<Boolean> = _isAutoDetecting.asStateFlow()

    private val _autoDetectStatus = MutableStateFlow<String?>(null)
    val autoDetectStatus: StateFlow<String?> = _autoDetectStatus.asStateFlow()

    private val _autoDetectSuccess = MutableStateFlow<Boolean?>(null)
    val autoDetectSuccess: StateFlow<Boolean?> = _autoDetectSuccess.asStateFlow()

    fun clearAutoDetectStatus() {
        _autoDetectStatus.value = null
        _autoDetectSuccess.value = null
    }

    fun resetVerificationState() {
        _verificationState.value = VerificationState.Idle
    }

    fun loadYoutubeKey(): String {
        val key = settingsRepository.getYoutubeApiKey()
        return if (key.isNullOrBlank()) BuildConfig.YOUTUBE_API_KEY else key
    }

    fun loadChatKey(provider: LlmProvider = loadLlmProvider()): String {
        val key = settingsRepository.getChatApiKey(provider)
        return if (key.isNullOrBlank()) {
            if (provider == LlmProvider.GROQ) BuildConfig.GROQ_API_KEY else ""
        } else {
            key
        }
    }

    fun loadEdgeTtsSubscriptionKey(): String {
        val key = settingsRepository.getEdgeTtsSubscriptionKey()
        return if (key.isNullOrBlank()) BuildConfig.EDGE_TTS_SUBSCRIPTION_KEY else key
    }
    
    fun loadLlmProvider(): LlmProvider {
        val name = settingsRepository.getLlmProvider()
        return try {
            LlmProvider.valueOf(name)
        } catch (e: Exception) {
            LlmProvider.GROQ
        }
    }

    fun loadLlmModel(): String = settingsRepository.getLlmModel()

    fun loadLlmCustomUrl(): String = settingsRepository.getLlmCustomUrl()
    fun loadLlmCustomHeaders(): String = settingsRepository.getLlmCustomHeaders()
    fun loadLlmCustomResponsePath(): String = settingsRepository.getLlmCustomResponsePath()
    fun loadLlmCustomRequestTemplate(): String = settingsRepository.getLlmCustomRequestTemplate()
    fun loadLlmCustomMessageFormat(): String = settingsRepository.getLlmCustomMessageFormat()
    fun loadLlmCustomSystemRole(): String = settingsRepository.getLlmCustomSystemRole()
    fun loadLlmCustomUserRole(): String = settingsRepository.getLlmCustomUserRole()
    fun loadLlmCustomAssistantRole(): String = settingsRepository.getLlmCustomAssistantRole()
    fun loadUseLocalWhisper(): Boolean = settingsRepository.getUseLocalWhisper()
    fun loadSttMode(): com.app.assistant.speech.SttMode = settingsRepository.getSttMode()

    fun loadTtsMode(): com.app.assistant.tts.TtsMode = settingsRepository.getTtsMode()


    fun deleteModel() {
        viewModelScope.launch(Dispatchers.IO) {
            modelManager.deleteLocalModel()
            _isModelInstalled.value = false
            val currentMode = settingsRepository.getSttMode()
            if (currentMode != com.app.assistant.speech.SttMode.NATIVE) {
                settingsRepository.setSttMode(com.app.assistant.speech.SttMode.NATIVE)
            }
            _modelDownloadState.value = DownloadState.Idle
        }
    }

    fun cancelModelDownload() {
        modelManager.cancelDownload()
        _modelDownloadState.value = DownloadState.Idle
    }

    fun startModelDownload() {
        viewModelScope.launch {
            _modelDownloadState.value = DownloadState.Downloading(0f, 0L, modelManager.totalSizeBytes)
            modelManager.downloadModel(
                client = okHttpClient,
                onProgress = { downloadedBytes, totalBytes ->
                    val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                    _modelDownloadState.value = DownloadState.Downloading(progress, downloadedBytes, totalBytes)
                },
                onComplete = { success, errorMsg ->
                    viewModelScope.launch {
                        if (success) {
                            _modelDownloadState.value = DownloadState.Completed
                            _isModelInstalled.value = true
                        } else {
                            _modelDownloadState.value = DownloadState.Error(errorMsg ?: "Unknown download error")
                            _isModelInstalled.value = modelManager.isModelDownloaded()
                        }
                    }
                }
            )
        }
    }

    fun startTtsModelDownload() {
        viewModelScope.launch {
            _ttsModelDownloadState.value = DownloadState.Downloading(0f, 0L, 0L)
            ttsModelManager.downloadModel(
                client = okHttpClient,
                onProgress = { downloadedBytes, totalBytes ->
                    val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                    _ttsModelDownloadState.value = DownloadState.Downloading(progress, downloadedBytes, totalBytes)
                },
                onComplete = { success, errorMsg ->
                    viewModelScope.launch {
                        if (success) {
                            _ttsModelDownloadState.value = DownloadState.Completed
                            _isTtsModelInstalled.value = true
                        } else {
                            _ttsModelDownloadState.value = DownloadState.Error(errorMsg ?: "Unknown download error")
                            _isTtsModelInstalled.value = ttsModelManager.isModelDownloaded()
                        }
                    }
                }
            )
        }
    }

    fun cancelTtsModelDownload() {
        ttsModelManager.cancelDownload()
        _ttsModelDownloadState.value = DownloadState.Idle
    }

    fun deleteTtsModel() {
        viewModelScope.launch(Dispatchers.IO) {
            ttsModelManager.deleteLocalModel()
            _isTtsModelInstalled.value = false
            _ttsModelDownloadState.value = DownloadState.Idle
            val currentMode = settingsRepository.getTtsMode()
            if (currentMode != com.app.assistant.tts.TtsMode.NATIVE) {
                settingsRepository.setTtsMode(com.app.assistant.tts.TtsMode.NATIVE)
            }
        }
    }

    fun saveSettings(
        youtubeApiKey: String,
        chatApiKey: String,
        edgeTtsSubscriptionKey: String,
        provider: LlmProvider,
        model: String,
        customUrl: String,
        customHeaders: String,
        customResponsePath: String,
        customRequestTemplate: String,
        customMessageFormat: String,
        customSystemRole: String,
        customUserRole: String,
        customAssistantRole: String,
        sttMode: com.app.assistant.speech.SttMode,
        ttsMode: com.app.assistant.tts.TtsMode
    ) {
        settingsRepository.saveKeys(youtubeApiKey, chatApiKey, edgeTtsSubscriptionKey)
        settingsRepository.setLlmProvider(provider.name)
        settingsRepository.setLlmModel(model)
        settingsRepository.setLlmCustomUrl(customUrl)
        settingsRepository.setLlmCustomHeaders(customHeaders)
        settingsRepository.setLlmCustomResponsePath(customResponsePath)
        settingsRepository.setLlmCustomRequestTemplate(customRequestTemplate)
        settingsRepository.setLlmCustomMessageFormat(customMessageFormat)
        settingsRepository.setLlmCustomSystemRole(customSystemRole)
        settingsRepository.setLlmCustomUserRole(customUserRole)
        settingsRepository.setLlmCustomAssistantRole(customAssistantRole)
        settingsRepository.setSttMode(sttMode)
        settingsRepository.setTtsMode(ttsMode)
    }

    fun updateYoutubeApiKey(key: String) {
        settingsRepository.saveKeys(
            youtubeApiKey = key,
            chatApiKey = settingsRepository.getChatApiKey() ?: "",
            edgeTtsSubscriptionKey = settingsRepository.getEdgeTtsSubscriptionKey() ?: ""
        )
    }

    fun updateChatApiKey(key: String, provider: LlmProvider = loadLlmProvider()) {
        settingsRepository.saveChatApiKey(provider, key)
    }

    fun updateEdgeTtsSubscriptionKey(key: String) {
        settingsRepository.saveKeys(
            youtubeApiKey = settingsRepository.getYoutubeApiKey() ?: "",
            chatApiKey = settingsRepository.getChatApiKey() ?: "",
            edgeTtsSubscriptionKey = key
        )
    }

    fun updateLlmProvider(provider: LlmProvider) {
        settingsRepository.setLlmProvider(provider.name)
    }

    fun updateLlmModel(model: String) {
        settingsRepository.setLlmModel(model)
    }

    fun updateLlmCustomUrl(url: String) {
        settingsRepository.setLlmCustomUrl(url)
    }

    fun updateLlmCustomHeaders(headers: String) {
        settingsRepository.setLlmCustomHeaders(headers)
    }

    fun updateLlmCustomResponsePath(path: String) {
        settingsRepository.setLlmCustomResponsePath(path)
    }

    fun updateLlmCustomRequestTemplate(template: String) {
        settingsRepository.setLlmCustomRequestTemplate(template)
    }

    fun updateLlmCustomMessageFormat(format: String) {
        settingsRepository.setLlmCustomMessageFormat(format)
    }

    fun updateLlmCustomSystemRole(role: String) {
        settingsRepository.setLlmCustomSystemRole(role)
    }

    fun updateLlmCustomUserRole(role: String) {
        settingsRepository.setLlmCustomUserRole(role)
    }

    fun updateLlmCustomAssistantRole(role: String) {
        settingsRepository.setLlmCustomAssistantRole(role)
    }

    fun updateSttMode(mode: com.app.assistant.speech.SttMode) {
        settingsRepository.setSttMode(mode)
    }

    fun updateTtsMode(mode: com.app.assistant.tts.TtsMode) {
        settingsRepository.setTtsMode(mode)
    }

    fun autoDetectAndApplyConfig(
        baseUrl: String,
        apiKey: String,
        onApplied: ((DetectedServerConfig) -> Unit)? = null
    ) {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) {
            _autoDetectStatus.value = "Please enter an API Endpoint URL first."
            _autoDetectSuccess.value = false
            return
        }
        viewModelScope.launch {
            _isAutoDetecting.value = true
            _autoDetectStatus.value = "Detecting server format..."
            _autoDetectSuccess.value = null

            try {
                val detected = ModelCapabilityProber.autoDetectCustomServer(customConfigHttpClient, trimmed, apiKey)
                if (detected != null) {
                    updateLlmCustomUrl(detected.completionUrl)
                    val headersJson = if (detected.headers.isNotEmpty()) {
                        val entries = detected.headers.map { "\"${it.key}\": \"${it.value.replace("\"", "\\\"")}\"" }
                        "{${entries.joinToString(", ")}}"
                    } else "{}"
                    updateLlmCustomHeaders(headersJson)
                    updateLlmCustomResponsePath(detected.responsePath)
                    updateLlmCustomRequestTemplate(detected.requestTemplate)
                    updateLlmCustomMessageFormat(detected.messageFormat)
                    updateLlmCustomSystemRole(detected.systemRole ?: "")
                    updateLlmCustomUserRole(detected.userRole)
                    updateLlmCustomAssistantRole(detected.assistantRole)

                    if (detected.availableModels.isNotEmpty()) {
                        _fetchedModels.value = _fetchedModels.value + (LlmProvider.CUSTOM to detected.availableModels)
                        val currentModel = loadLlmModel()
                        if (currentModel.isBlank() || currentModel == LlmProvider.CUSTOM.defaultModel || !detected.availableModels.contains(currentModel)) {
                            val selectedModel = detected.availableModels.first()
                            updateLlmModel(selectedModel)
                        }
                        _autoDetectStatus.value = "Detected ${detected.serverType}! Found ${detected.availableModels.size} model(s)."
                    } else {
                        _autoDetectStatus.value = "Detected ${detected.serverType}! Endpoint configured."
                    }
                    _autoDetectSuccess.value = true
                    resetVerificationState()
                    onApplied?.invoke(detected)
                } else {
                    _autoDetectStatus.value = "Could not auto-detect format. Using defaults; please check URL or configure below."
                    _autoDetectSuccess.value = false
                }
            } catch (e: Exception) {
                _autoDetectStatus.value = e.message ?: "Detection failed: ${e.message}"
                _autoDetectSuccess.value = false
            } finally {
                _isAutoDetecting.value = false
            }
        }
    }

    fun applyCustomPreset(
        preset: CustomServerPreset,
        currentUrl: String,
        apiKey: String,
        onApplied: ((url: String, headers: String, respPath: String, template: String, format: String, sysRole: String, userRole: String, assRole: String) -> Unit)? = null
    ) {
        val trimmed = currentUrl.trim()
        when (preset) {
            CustomServerPreset.OPENAI_COMPATIBLE -> {
                val resolvedUrl = if (trimmed.isNotBlank()) LlmConfig.resolveCompletionUrl(trimmed) else "https://api.example.com/v1/chat/completions"
                val headersJson = if (apiKey.isNotBlank()) {
                    "{\"Authorization\": \"Bearer {{API_KEY}}\", \"Content-Type\": \"application/json\"}"
                } else {
                    "{\"Content-Type\": \"application/json\"}"
                }
                val respPath = "choices[0].message.content"
                val template = "{\n  \"model\": \"{{MODEL}}\",\n  \"messages\": {{MESSAGES}}\n}"
                val format = "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}"
                val sysRole = "system"
                val userRole = "user"
                val assRole = "assistant"

                updateLlmCustomUrl(resolvedUrl)
                updateLlmCustomHeaders(headersJson)
                updateLlmCustomResponsePath(respPath)
                updateLlmCustomRequestTemplate(template)
                updateLlmCustomMessageFormat(format)
                updateLlmCustomSystemRole(sysRole)
                updateLlmCustomUserRole(userRole)
                updateLlmCustomAssistantRole(assRole)
                resetVerificationState()
                onApplied?.invoke(resolvedUrl, headersJson, respPath, template, format, sysRole, userRole, assRole)
            }
            CustomServerPreset.OLLAMA_NATIVE -> {
                val resolvedUrl = if (trimmed.isNotBlank()) {
                    val withoutSlash = trimmed.trimEnd('/')
                    if (withoutSlash.endsWith("/api/chat")) withoutSlash else "$withoutSlash/api/chat"
                } else "http://localhost:11434/api/chat"
                val headersJson = "{\"Content-Type\": \"application/json\"}"
                val respPath = "message.content"
                val template = "{\n  \"model\": \"{{MODEL}}\",\n  \"messages\": {{MESSAGES}},\n  \"stream\": false\n}"
                val format = "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}"
                val sysRole = "system"
                val userRole = "user"
                val assRole = "assistant"

                updateLlmCustomUrl(resolvedUrl)
                updateLlmCustomHeaders(headersJson)
                updateLlmCustomResponsePath(respPath)
                updateLlmCustomRequestTemplate(template)
                updateLlmCustomMessageFormat(format)
                updateLlmCustomSystemRole(sysRole)
                updateLlmCustomUserRole(userRole)
                updateLlmCustomAssistantRole(assRole)
                resetVerificationState()
                onApplied?.invoke(resolvedUrl, headersJson, respPath, template, format, sysRole, userRole, assRole)
            }
            CustomServerPreset.ANTHROPIC_COMPATIBLE -> {
                val resolvedUrl = if (trimmed.isNotBlank()) {
                    val withoutSlash = trimmed.trimEnd('/')
                    if (withoutSlash.endsWith("/v1/messages")) withoutSlash else "$withoutSlash/v1/messages"
                } else "https://api.anthropic.com/v1/messages"
                val headersJson = "{\"x-api-key\": \"{{API_KEY}}\", \"anthropic-version\": \"2023-06-01\", \"Content-Type\": \"application/json\"}"
                val respPath = "content[0].text"
                val template = "{\n  \"model\": \"{{MODEL}}\",\n  \"max_tokens\": 1024,\n  \"system\": \"{{SYSTEM_CONTEXT}}\",\n  \"messages\": {{MESSAGES}}\n}"
                val format = "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}"
                val sysRole = ""
                val userRole = "user"
                val assRole = "assistant"

                updateLlmCustomUrl(resolvedUrl)
                updateLlmCustomHeaders(headersJson)
                updateLlmCustomResponsePath(respPath)
                updateLlmCustomRequestTemplate(template)
                updateLlmCustomMessageFormat(format)
                updateLlmCustomSystemRole(sysRole)
                updateLlmCustomUserRole(userRole)
                updateLlmCustomAssistantRole(assRole)
                resetVerificationState()
                onApplied?.invoke(resolvedUrl, headersJson, respPath, template, format, sysRole, userRole, assRole)
            }
            CustomServerPreset.CUSTOM -> {
                // Keep manual values
            }
        }
    }


    fun verifyModelAndSaveCapabilities(
        provider: LlmProvider,
        model: String,
        apiKey: String,
        customUrl: String,
        customHeaders: String,
        customResponsePath: String,
        customRequestTemplate: String,
        customMessageFormat: String,
        customSystemRole: String,
        customUserRole: String,
        customAssistantRole: String
    ) {
        viewModelScope.launch {
            _verificationState.value = VerificationState.Verifying
            
            try {
                val config = if (provider == LlmProvider.CUSTOM) {
                    LlmConfig(
                        url = customUrl,
                        headers = parseHeadersJson(customHeaders),
                        responsePath = customResponsePath,
                        requestTemplate = customRequestTemplate,
                        messageFormat = customMessageFormat,
                        systemRole = customSystemRole.takeIf { it.isNotBlank() },
                        userRole = customUserRole,
                        assistantRole = customAssistantRole
                    )
                } else {
                    provider.config
                }

                val clientToUse = if (provider == LlmProvider.CUSTOM) customConfigHttpClient else okHttpClient

                // 1. Test Text connection
                val isTextOk = ModelCapabilityProber.probeTextConnection(
                    client = clientToUse,
                    apiKey = apiKey,
                    model = model,
                    config = config
                )

                if (!isTextOk) {
                    _verificationState.value = VerificationState.Error("Connection check failed. Please verify API key, URL, or internet connection.")
                    settingsRepository.setIsModelVerified(false)
                    return@launch
                }

                // 2. Test Vision (Image) connection
                val isVisionOk = ModelCapabilityProber.probeVisionConnection(
                    client = clientToUse,
                    provider = provider,
                    apiKey = apiKey,
                    model = model,
                    config = config
                )

                // 3. Audio/Video checks (currently Gemini 1.5/2.0 is the main provider that supports audio/video input natively in the API)
                val hasAudio = provider == LlmProvider.GEMINI && (model.contains("1.5") || model.contains("2.0") || model.contains("2.5"))
                val hasVideo = provider == LlmProvider.GEMINI && (model.contains("1.5") || model.contains("2.0") || model.contains("2.5"))
                val hasDoc = (provider == LlmProvider.GEMINI && (model.contains("1.5") || model.contains("2.0") || model.contains("2.5"))) || (provider == LlmProvider.ANTHROPIC && model.contains("claude-3"))

                val capabilities = ModelCapabilities(
                    hasImageInput = isVisionOk,
                    hasAudioInput = hasAudio,
                    hasVideoInput = hasVideo,
                    hasDocumentInput = hasDoc
                )

                // Save detected capabilities in repository
                settingsRepository.setIsImageSupported(isVisionOk)
                settingsRepository.setIsAudioSupported(hasAudio)
                settingsRepository.setIsVideoSupported(hasVideo)
                settingsRepository.setIsDocumentSupported(hasDoc)
                settingsRepository.setIsModelVerified(true)

                _verificationState.value = VerificationState.Success(capabilities)

                // Auto-fetch models on successful connection verification
                fetchModelsForProvider(
                    provider = provider,
                    apiKey = apiKey,
                    customUrl = customUrl,
                    customHeaders = customHeaders
                )
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Connection check failed. Please verify API key, URL, or internet connection."
                _verificationState.value = VerificationState.Error(errorMsg)
                settingsRepository.setIsModelVerified(false)
            }
        }
    }

    fun fetchModelsForProvider(
        provider: LlmProvider,
        apiKey: String,
        customUrl: String = "",
        customHeaders: String = ""
    ) {
        viewModelScope.launch {
            if (apiKey.isBlank() && provider != LlmProvider.OLLAMA && provider != LlmProvider.OPEN_ROUTER && provider != LlmProvider.CUSTOM) {
                _modelFetchError.value = "API Key is required to fetch models."
                return@launch
            }
            _isFetchingModels.value = true
            _modelFetchError.value = null
            try {
                val clientToUse = if (provider == LlmProvider.CUSTOM) customConfigHttpClient else okHttpClient
                val models = ModelCapabilityProber.fetchAvailableModels(
                    client = clientToUse,
                    provider = provider,
                    apiKey = apiKey,
                    customUrl = customUrl,
                    customHeaders = customHeaders
                )
                if (models.isNotEmpty()) {
                    _fetchedModels.value = _fetchedModels.value + (provider to models)
                } else {
                    _modelFetchError.value = "No models returned from API."
                }
            } catch (e: Exception) {
                _modelFetchError.value = e.message ?: "Failed to fetch models: ${e.message}"
            } finally {
                _isFetchingModels.value = false
            }
        }
    }


    private fun parseHeadersJson(jsonStr: String): Map<String, String> {
        return try {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(jsonStr)
            if (element is kotlinx.serialization.json.JsonObject) {
                element.mapValues { (_, value) ->
                    if (value is kotlinx.serialization.json.JsonPrimitive) value.content else value.toString()
                }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun updateTranslationEnabled(enabled: Boolean) {
        _isTranslationEnabled.value = enabled
        settingsRepository.setTranslationEnabled(enabled)
    }

    fun updateActiveLanguageCode(languageCode: String) {
        _activeLanguageCode.value = languageCode
        settingsRepository.setActiveLanguageCode(languageCode)
    }

    fun refreshDownloadedTranslationModels() {
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { downloadedModels ->
                val langs = downloadedModels.map { it.language }.toSet()
                _downloadedTranslationLanguages.value = langs
            }
            .addOnFailureListener { e ->
                Log.e("SettingsViewModel", "Failed to fetch downloaded translation models: ${e.message}")
            }
    }

    fun downloadTranslationModel(languageCode: String, onResult: (Boolean) -> Unit = {}) {
        _downloadingTranslationLanguages.value = _downloadingTranslationLanguages.value + languageCode
        val modelManager = RemoteModelManager.getInstance()
        val languageModel = TranslateRemoteModel.Builder(languageCode).build()
        val conditions = DownloadConditions.Builder().build()

        modelManager.download(languageModel, conditions)
            .addOnSuccessListener {
                _downloadingTranslationLanguages.value = _downloadingTranslationLanguages.value - languageCode
                refreshDownloadedTranslationModels()
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e("SettingsViewModel", "Failed to download model for $languageCode: ${e.message}")
                _downloadingTranslationLanguages.value = _downloadingTranslationLanguages.value - languageCode
                onResult(false)
            }
    }

    fun deleteTranslationModel(languageCode: String) {
        val modelManager = RemoteModelManager.getInstance()
        val languageModel = TranslateRemoteModel.Builder(languageCode).build()
        modelManager.deleteDownloadedModel(languageModel)
            .addOnSuccessListener {
                refreshDownloadedTranslationModels()
            }
            .addOnFailureListener { e ->
                Log.e("SettingsViewModel", "Failed to delete model for $languageCode: ${e.message}")
            }
    }

    private fun getPublicStaticFinalStringsWithNames(clazz: Class<*>): List<Pair<String, String>> {
        val publicStaticFinalStringsWithNames = mutableListOf<Pair<String, String>>()
        val fields = clazz.declaredFields
        for (field in fields) {
            val modifiers = field.modifiers
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                if (field.type == String::class.java) {
                    try {
                        val name = field.name
                        val value = field.get(null) as String
                        publicStaticFinalStringsWithNames.add(Pair(name, value))
                    } catch (e: IllegalAccessException) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return publicStaticFinalStringsWithNames
    }
}
