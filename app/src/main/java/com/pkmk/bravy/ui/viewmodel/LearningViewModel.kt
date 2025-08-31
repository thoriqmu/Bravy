package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.pkmk.bravy.data.model.LearningLevel
import com.pkmk.bravy.data.model.LearningScene
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

    private val _showMicControls = MutableLiveData<Pair<Boolean, Int?>>()
    val showMicControls: LiveData<Pair<Boolean, Int?>> = _showMicControls

    private val _showAnalysisButton = MutableLiveData<Boolean>()
    val showAnalysisButton: LiveData<Boolean> = _showAnalysisButton

    private val _analysisResult = MutableLiveData<Pair<Int, Int>>()
    val analysisResult: LiveData<Pair<Int, Int>> = _analysisResult

    // LiveData untuk mengirim hasil analisis suara dari Activity ke Fragment
    private val _speechResult = MutableLiveData<String>()
    val speechResult: LiveData<String> = _speechResult

    private val _finalPracticeScore = MutableLiveData<Int>()
    val finalPracticeScore: LiveData<Int> = _finalPracticeScore

    private var accumulatedScore = 0
    private var practiceCount = 0

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var currentShadowingScene: LearningScene? = null

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

    fun setMicControlsVisibility(isVisible: Boolean, duration: Int? = null) {
        _showMicControls.postValue(isVisible to duration)
    }

    fun postSpeechResult(resultType: String) {
        _speechResult.postValue(resultType)
    }

    // Panggil ini dari Fragment saat video shadowing selesai
    fun requestAnalysis(scene: LearningScene) {
        currentShadowingScene = scene
        _showAnalysisButton.postValue(true)
    }

    // Fungsi untuk menyembunyikan tombol setelah analisis
    fun hideAnalysisButton() {
        _showAnalysisButton.postValue(false)
    }

    fun getCurrentShadowingScene(): LearningScene? {
        return currentShadowingScene
    }

    fun postAnalysisResult(confidencePoints: Int, speechPoints: Int) {
        val totalScore = confidencePoints + speechPoints
        accumulatedScore += totalScore
        practiceCount++
        _analysisResult.postValue(confidencePoints to speechPoints)
    }

    // Panggil fungsi ini saat section shadowing selesai
    fun calculateFinalScore() {
        if (practiceCount > 0) {
            val averageScore = accumulatedScore / practiceCount
            _finalPracticeScore.postValue(averageScore)
        } else {
            _finalPracticeScore.postValue(0)
        }
    }

    // Reset skor saat level baru dimuat untuk menghindari data lama
    fun resetScores() {
        accumulatedScore = 0
        practiceCount = 0
    }
}