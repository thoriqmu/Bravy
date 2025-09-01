package com.pkmk.bravy.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.pkmk.bravy.data.model.LearningLevel
import com.pkmk.bravy.data.model.LearningScene
import com.pkmk.bravy.data.model.LearningSection
import com.pkmk.bravy.data.model.PracticeResult
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

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _lastPlayedVideoUri = MutableLiveData<Uri?>()
    val lastPlayedVideoUri: LiveData<Uri?> = _lastPlayedVideoUri

    private val _showResultDialog = MutableLiveData<PracticeResult>()
    val showResultDialog: LiveData<PracticeResult> = _showResultDialog

    private var accumulatedScore = 0
    private var totalConfidenceScore = 0
    private var totalSpeechScore = 0
    private var practiceCount = 0
    private var currentShadowingScene: LearningScene? = null

    fun loadLevelData(levelId: String) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            try {
                val levelSnapshot = database.getReference("learning_levels/$levelId").get().await()
                val progressSnapshot = database
                    .getReference("users/$userId/user_progress/$levelId")
                    .get().await()

                val level = levelSnapshot.getValue(LearningLevel::class.java)
                val progress = progressSnapshot.getValue(UserProgress::class.java)

                _learningLevel.postValue(level)

                val processedSections = level?.sections?.values
                    ?.sortedBy { it.order }
                    ?.mapIndexed { index, section ->
                        val previousSectionOrder = index
                        val previousSectionId = level.sections.values.firstOrNull { it.order == previousSectionOrder }?.sectionId
                        section.copy(isLocked = when {
                            index == 0 -> false
                            previousSectionId == null -> true
                            else -> !(progress?.completed_sections?.get(previousSectionId) ?: false)
                        })
                    } ?: emptyList()
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
                database
                    .getReference("users/$userId/user_progress/$levelId/completed_sections/$sectionId")
                    .setValue(true).await()
                loadLevelData(levelId)
            } catch (e: Exception) {
                _error.postValue("Failed to update progress: ${e.message}")
            }
        }
    }

    fun setLastPlayedVideoUri(uri: Uri?) {
        _lastPlayedVideoUri.postValue(uri)
    }

    fun setMicControlsVisibility(isVisible: Boolean, duration: Int? = null) {
        _showMicControls.postValue(isVisible to duration)
    }

    fun postSpeechResult(resultType: String) {
        _speechResult.postValue(resultType)
    }

    fun requestAnalysis(scene: LearningScene) {
        currentShadowingScene = scene
        _showAnalysisButton.postValue(true)
    }

    fun hideAnalysisButton() {
        _showAnalysisButton.postValue(false)
    }

    fun getCurrentShadowingScene(): LearningScene? {
        return currentShadowingScene
    }

    fun calculateFinalScore() {
        if (practiceCount > 0) {
            val avgTotal = (accumulatedScore.toFloat() / practiceCount).toInt()
            val avgConfidence = (totalConfidenceScore.toFloat() / practiceCount).toInt()
            val avgSpeech = (totalSpeechScore.toFloat() / practiceCount).toInt()

            val recommendation = generateRecommendation(avgConfidence, avgSpeech)
            val confidenceRecommendation = generateConfidenceRecommendation(avgConfidence)
            val levelTitle = _learningLevel.value?.title ?: "Practice"
            val levelId = _learningLevel.value?.levelId ?: return

            val result = PracticeResult(avgConfidence, avgSpeech, avgTotal, recommendation, confidenceRecommendation, levelTitle)
            _showResultDialog.postValue(result)

            // Panggil fungsi update skor yang baru
            updateUserScores(levelId, avgTotal)
        }
    }

    private fun updateUserScores(levelId: String, newPracticeScore: Int) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val userProgressRef = database.getReference("users/$userId/user_progress/$levelId")
                val totalPointsRef = database.getReference("users/$userId/points")

                // 1. Ambil skor terbaik sebelumnya dari user_progress
                val progressSnapshot = userProgressRef.get().await()
                val currentProgress = progressSnapshot.getValue(UserProgress::class.java)
                val previousBestScore = currentProgress?.points ?: 0

                // 2. Bandingkan dengan skor baru
                if (newPracticeScore > previousBestScore) {
                    // Hitung selisih poin yang akan ditambahkan
                    val pointsDifference = newPracticeScore - previousBestScore

                    // 3. Update skor terbaik di user_progress/{levelId}/points
                    userProgressRef.child("points").setValue(newPracticeScore).await()

                    // 4. Update total poin pengguna
                    val totalPointsSnapshot = totalPointsRef.get().await()
                    val currentTotalPoints = totalPointsSnapshot.getValue(Int::class.java) ?: 0
                    val newTotalPoints = currentTotalPoints + pointsDifference
                    totalPointsRef.setValue(newTotalPoints).await()

                    _error.postValue("Congratulations! Your score improved by $pointsDifference points.")
                } else {
                    _error.postValue("You've completed the practice. Try to beat your high score of $previousBestScore!")
                }

            } catch (e: Exception) {
                _error.postValue("Failed to update points: ${e.message}")
            }
        }
    }

    private fun generateRecommendation(confidence: Int, speech: Int): String {
        return when {
            confidence >= 4 && speech >= 9 -> "Excellent work! Your confidence and speech accuracy are both outstanding. Keep practicing to maintain this great level!"
            confidence < 3 && speech >= 9 -> "Your speech is very clear, great job! Try to be more relaxed. Maintain good eye contact with the camera to boost your confidence score."
            confidence >= 4 && speech < 5 -> "You look very confident! Let's work on pronunciation. Try listening to the sentence a few more times and repeat it slowly."
            else -> "Good start! Consistent practice is key. Try repeating this level to improve both your confidence and speech accuracy."
        }
    }

    private fun generateConfidenceRecommendation(confidence: Int): String {
        return when (confidence) {
            5 -> "Excellent confidence! Keep it up."
            4 -> "Great confidence, maintain this level."
            3 -> "Decent confidence, try to stay calmer."
            2 -> "You seem a bit tense, relax and focus."
            else -> "Low confidence, practice more to feel comfortable."
        }
    }

    private fun generateSpeechRecommendation(speech: Int): String {
        return when {
            speech >= 9 -> "Your pronunciation is excellent and very clear!"
            speech >= 7 -> "Good pronunciation, just a bit more polishing."
            speech >= 5 -> "Fair pronunciation, focus on word clarity."
            speech >= 3 -> "Pronunciation needs improvement, practice slowly."
            else -> "Significant practice needed, listen carefully and repeat."
        }
    }

    fun updateUserPoints(pointsToAdd: Int) {
        val userId = auth.currentUser?.uid ?: return
        val userPointsRef = database.getReference("users/$userId/points")

        userPointsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentPoints = snapshot.getValue(Int::class.java) ?: 0
                val newTotalPoints = currentPoints + pointsToAdd
                userPointsRef.setValue(newTotalPoints)
            }
            override fun onCancelled(error: DatabaseError) {
                _error.postValue("Failed to update points: ${error.message}")
            }
        })
    }

    fun postAnalysisResult(confidencePoints: Int, speechPoints: Int) {
        accumulatedScore += (confidencePoints + speechPoints)
        totalConfidenceScore += confidencePoints
        totalSpeechScore += speechPoints
        practiceCount++
        _analysisResult.postValue(confidencePoints to speechPoints)
    }

    // Ubah resetScores
    fun resetScores() {
        accumulatedScore = 0
        practiceCount = 0
        totalConfidenceScore = 0
        totalSpeechScore = 0
        _lastPlayedVideoUri.postValue(null)
    }
}