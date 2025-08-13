package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.pkmk.bravy.data.model.LearningLevel
import com.pkmk.bravy.data.model.LearningSection
import com.pkmk.bravy.data.model.UserProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class LearningViewModel @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _learningLevel = MutableLiveData<LearningLevel?>()
    val learningLevel: LiveData<LearningLevel?> = _learningLevel

    private val _sections = MutableLiveData<List<LearningSection>>()
    val sections: LiveData<List<LearningSection>> = _sections

    private val _showMicControls = MutableLiveData<Boolean>()
    val showMicControls: LiveData<Boolean> = _showMicControls

    // LiveData untuk mengirim hasil analisis suara dari Activity ke Fragment
    private val _speechResult = MutableLiveData<String>()
    val speechResult: LiveData<String> = _speechResult

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun loadLevelData(levelId: String) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            try {
                // Ambil data level
                val levelSnapshot = database.getReference("learning_levels/$levelId").get().await()

                // PERUBAHAN DI SINI: Path untuk mengambil user_progress
                val progressSnapshot = database
                    .getReference("users/$userId/user_progress/$levelId") // <-- Path baru
                    .get().await()

                val level = levelSnapshot.getValue(LearningLevel::class.java)
                val progress = progressSnapshot.getValue(UserProgress::class.java)

                if (level == null) {
                    _error.postValue("Level data not found.")
                    return@launch
                }
                _learningLevel.postValue(level)

                // Logika proses section tidak perlu diubah, karena sudah benar
                val processedSections = level.sections.values
                    .sortedBy { it.order }
                    .mapIndexed { index, section ->
                        // ... (logika isLocked tetap sama) ...
                        val previousSectionOrder = index
                        val previousSectionId = level.sections.values.firstOrNull { it.order == previousSectionOrder }?.sectionId

                        section.copy(isLocked = when {
                            index == 0 -> false // Section pertama selalu terbuka
                            previousSectionId == null -> true // Jika tidak ada section sebelumnya (seharusnya tidak terjadi)
                            else -> !(progress?.completed_sections?.get(previousSectionId) ?: false)
                        })
                    }
                _sections.postValue(processedSections)

            } catch (e: Exception) {
                _error.postValue("Failed to load data: ${e.message}")
            }
        }
    }

    fun completeSection(levelId: String, sectionId: String) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            try {
                // PERUBAHAN DI SINI: Path untuk menyimpan user_progress
                database
                    .getReference("users/$userId/user_progress/$levelId/completed_sections/$sectionId") // <-- Path baru
                    .setValue(true).await()
                // Muat ulang data untuk memperbarui UI
                loadLevelData(levelId)
            } catch (e: Exception) {
                _error.postValue("Failed to update progress: ${e.message}")
            }
        }
    }

    fun setMicControlsVisibility(isVisible: Boolean) {
        _showMicControls.postValue(isVisible)
    }

    fun postSpeechResult(resultType: String) {
        _speechResult.postValue(resultType)
    }
}