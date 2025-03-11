/*
 * Updated: 2025-01-21 20:37:16
 * Author: William8677
 */

package com.williamfq.xhat.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.williamfq.xhat.domain.model.GalleryImage
import com.williamfq.xhat.domain.repository.GalleryRepository
import com.williamfq.xhat.utils.image.ImageProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val galleryRepository: GalleryRepository,
    private val imageProcessor: ImageProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadImages()
    }

    private fun loadImages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val images = galleryRepository.getImages()
                _uiState.update { state ->
                    state.copy(
                        images = images,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = "Error al cargar imágenes: ${e.message}"
                    )
                }
            }
        }
    }

    fun selectImage(image: GalleryImage) {
        _uiState.update { it.copy(selectedImage = image) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedImage = null) }
    }

    fun deleteSelectedImages() {
        viewModelScope.launch {
            val imagesToDelete = _uiState.value.selectedImages
            if (imagesToDelete.isEmpty()) return@launch

            _uiState.update { it.copy(isProcessing = true) }
            try {
                galleryRepository.deleteImages(imagesToDelete)
                loadImages()
                _uiState.update { state ->
                    state.copy(
                        selectedImages = emptyList(),
                        isProcessing = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        error = "Error al eliminar imágenes: ${e.message}"
                    )
                }
            }
        }
    }

    fun shareImages(images: List<GalleryImage>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                galleryRepository.shareImages(images)
                _uiState.update { it.copy(isProcessing = false) }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        error = "Error al compartir imágenes: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class GalleryUiState(
    val images: List<GalleryImage> = emptyList(),
    val selectedImage: GalleryImage? = null,
    val selectedImages: List<GalleryImage> = emptyList(),
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null
)