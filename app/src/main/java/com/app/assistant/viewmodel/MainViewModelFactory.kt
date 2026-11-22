package com.app.assistant.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.app.assistant.db.DynamicConversationRepository
import com.app.assistant.repository.ContactsRepository
import com.app.assistant.repository.SettingsRepository
import com.app.assistant.repository.WeatherRepository
import com.app.assistant.usecase.CallContactUseCase
import com.app.assistant.usecase.GetAiResponseUseCase
import com.app.assistant.usecase.GetWeatherUseCase
import com.app.assistant.usecase.NavigateUseCase
import com.app.assistant.usecase.PlaySongUseCase
import com.app.assistant.usecase.ProcessChatCommandUseCase
import com.app.assistant.usecase.SetAlarmUseCase
import com.app.assistant.usecase.SetReminderUseCase
import com.app.assistant.network.LocalOnlyCleartextInterceptor
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainViewModelFactory(
    private val application: Application,
    private val speak: Boolean,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val settingsRepo = SettingsRepository(application)
            val contactsRepo = ContactsRepository(application)
            val dbRepo = DynamicConversationRepository(application)
            
            val okHttpClient = okHttpClient
            val customConfigHttpClient = customConfigHttpClient

            val weatherRepo = WeatherRepository(application, okHttpClient)

            val permissionChecker = AndroidPermissionChecker(application)
            val resourceProvider = AndroidResourceProvider(application)

            val getAiResponseUseCase = GetAiResponseUseCase(settingsRepo, okHttpClient, customConfigHttpClient)
            val callContactUseCase = CallContactUseCase(permissionChecker, contactsRepo)
            val playSongUseCase = PlaySongUseCase(resourceProvider, settingsRepo, okHttpClient)
            val navigateUseCase = NavigateUseCase(resourceProvider)
            val getWeatherUseCase = GetWeatherUseCase(permissionChecker, weatherRepo, getAiResponseUseCase)
            val setAlarmUseCase = SetAlarmUseCase(resourceProvider)
            val setReminderUseCase = SetReminderUseCase(resourceProvider)
            val processChatCommandUseCase = ProcessChatCommandUseCase(getAiResponseUseCase, settingsRepo)

            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                application,
                speak,
                settingsRepo,
                dbRepo,
                callContactUseCase,
                playSongUseCase,
                navigateUseCase,
                getWeatherUseCase,
                setAlarmUseCase,
                setReminderUseCase,
                processChatCommandUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

    companion object {
        val okHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
                .followSslRedirects(false)
                .build()
        }

        val customConfigHttpClient: OkHttpClient by lazy {
            okHttpClient.newBuilder()
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
                .followSslRedirects(false)
                .addInterceptor(LocalOnlyCleartextInterceptor())
                .build()
        }
    }
}
