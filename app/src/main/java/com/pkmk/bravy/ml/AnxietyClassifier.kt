package com.pkmk.bravy.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.ByteBuffer

class AnxietyClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var inputImageWidth: Int = 0
    private var inputImageHeight: Int = 0

    // PERBAIKAN 1: Tambahkan flag untuk melacak status interpreter
    @Volatile
    private var isClosed = false

    private val labels = listOf("very relaxed", "relaxed", "mildly anxious", "anxious", "very anxious")

    init {
        try {
            val model = FileUtil.loadMappedFile(context, "kecemasan_model.tflite")
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(model, options)

            val inputShape = interpreter?.getInputTensor(0)?.shape()
            if (inputShape != null && inputShape.size >= 3) {
                inputImageHeight = inputShape[1]
                inputImageWidth = inputShape[2]
            } else {
                inputImageHeight = 224
                inputImageWidth = 224
            }
        } catch (e: IOException) {
            Log.e("AnxietyClassifier", "Error initializing TFLite Interpreter.", e)
        }
    }

    // PERBAIKAN 2: Gunakan synchronized untuk memastikan thread-safety
    fun classify(bitmap: Bitmap): Int = synchronized(this) {
        if (interpreter == null || isClosed) {
            Log.e("AnxietyClassifier", "Classifier not initialized or has been closed.")
            return 0
        }

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, labels.size), DataType.FLOAT32)

        try {
            // PERBAIKAN 3: Gunakan buffer.rewind() di dalam blok try-catch
            val inputBuffer: ByteBuffer = tensorImage.buffer
            interpreter?.run(inputBuffer, outputBuffer.buffer.rewind())
        } catch (e: Exception) {
            Log.e("AnxietyClassifier", "Error running model inference.", e)
            return 0
        }

        val scores = outputBuffer.floatArray
        var maxScore = -1f
        var maxIndex = -1
        scores.forEachIndexed { index, score ->
            if (score > maxScore) {
                maxScore = score
                maxIndex = index
            }
        }

        val detectedLabel = if (maxIndex != -1) labels[maxIndex] else "unknown"
        Log.d("AnxietyClassifier", "Detected label: $detectedLabel with score: $maxScore")

        return mapLabelToPoints(detectedLabel)
    }

    private fun mapLabelToPoints(label: String?): Int {
        return when (label) {
            "very relaxed" -> 5
            "relaxed" -> 4
            "mildly anxious" -> 3
            "anxious" -> 2
            "very anxious" -> 1
            else -> 0
        }
    }

    // PERBAIKAN 4: Gunakan synchronized untuk menutup dengan aman
    fun close() = synchronized(this) {
        if (!isClosed) {
            interpreter?.close()
            interpreter = null
            isClosed = true
        }
    }
}