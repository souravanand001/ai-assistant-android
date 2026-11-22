package com.app.assistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.app.assistant.repository.SettingsRepository
import okhttp3.OkHttpClient

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient,
    private val customConfigHttpClient: OkHttpClient = MainViewModelFactory.customConfigHttpClient
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsRepository, okHttpClient, customConfigHttpClient) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
