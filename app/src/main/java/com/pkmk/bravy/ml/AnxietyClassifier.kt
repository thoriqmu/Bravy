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

class AnxietyClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var inputImageWidth: Int = 0
    private var inputImageHeight: Int = 0
    private val modelInputSize = 4 * 224 * 224 * 3 // Float32 * Lebar * Tinggi * 3 Channel (RGB)

    // Definisikan label sesuai dengan urutan output model Anda
    private val labels = listOf("very relaxed", "relaxed", "mildly anxious", "anxious", "very anxious")

    init {
        try {
            val model = FileUtil.loadMappedFile(context, "kecemasan_model.tflite")
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(model, options)

            // Dapatkan dimensi input dari model
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            if (inputShape != null && inputShape.size >= 3) {
                inputImageHeight = inputShape[1]
                inputImageWidth = inputShape[2]
            } else {
                // Fallback jika tidak bisa membaca shape
                inputImageHeight = 224
                inputImageWidth = 224
            }

        } catch (e: IOException) {
            Log.e("AnxietyClassifier", "Error initializing TFLite Interpreter.", e)
        }
    }

    fun classify(bitmap: Bitmap): Int {
        if (interpreter == null) {
            Log.e("AnxietyClassifier", "Classifier not initialized.")
            return 0
        }

        // 1. Preprocess the image
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // 2. Prepare the output buffer
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, labels.size), DataType.FLOAT32)

        // 3. Run inference
        try {
            interpreter?.run(tensorImage.buffer, outputBuffer.buffer.rewind())
        } catch (e: Exception) {
            Log.e("AnxietyClassifier", "Error running model inference.", e)
            return 0
        }


        // 4. Post-process the output
        val scores = outputBuffer.floatArray
        var maxScore = -1f
        var maxIndex = -1
        for (i in scores.indices) {
            if (scores[i] > maxScore) {
                maxScore = scores[i]
                maxIndex = i
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

    fun close() {
        interpreter?.close()
    }
}