/*
 * Updated: 2025-01-21 18:03:39
 * Author: William8677
 */

package com.williamfq.xhat.ui.screens.main.viewmodel

import androidx.lifecycle.ViewModel
import com.williamfq.xhat.ui.main.MainScreens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    private val _screenState = MutableStateFlow<MainScreens>(MainScreens.CHATS)
    val screenState: StateFlow<MainScreens> = _screenState.asStateFlow()

    private val _isMenuOpen = MutableStateFlow(false)
    val isMenuOpen: StateFlow<Boolean> = _isMenuOpen.asStateFlow()

    fun updateCurrentScreen(page: Int) {
        _screenState.value = MainScreens.entries.getOrNull(page) ?: MainScreens.CHATS
    }

    fun onMenuClick() {
        _isMenuOpen.value = !_isMenuOpen.value
    }

    fun closeMenu() {
        _isMenuOpen.value = false
    }

    fun navigateToScreen(screen: MainScreens) {
        _screenState.value = screen
        closeMenu()
    }
}