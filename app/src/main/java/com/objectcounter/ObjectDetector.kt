package com.objectcounter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class Detection(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)

class ObjectDetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    companion object {
        private const val MODEL_FILE = "yolov8n.tflite"
        private const val INPUT_SIZE = 640
        private const val CONF_THRESHOLD = 0.35f
        private const val IOU_THRESHOLD = 0.45f
        private const val NUM_CLASSES = 80

        val COCO_LABELS = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
            "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
            "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
            "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
            "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
            "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
            "hair drier", "toothbrush"
        )
    }

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                try {
                    gpuDelegate = GpuDelegate()
                    addDelegate(gpuDelegate!!)
                } catch (e: Exception) {
                    // GPU not available, use CPU
                }
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val interpreter = this.interpreter ?: return emptyList()

        // Preprocess
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = bitmapToByteBuffer(resized)

        // YOLOv8 output: [1, 84, 8400]
        val outputArray = Array(1) { Array(84) { FloatArray(8400) } }

        interpreter.run(inputBuffer, outputArray)

        return postProcess(outputArray[0], bitmap.width, bitmap.height)
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f) // R
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)  // G
            buffer.putFloat((pixel and 0xFF) / 255.0f)           // B
        }

        buffer.rewind()
        return buffer
    }

    private fun postProcess(
        output: Array<FloatArray>,
        imgWidth: Int,
        imgHeight: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numDetections = output[0].size // 8400

        for (i in 0 until numDetections) {
            val cx = output[0][i]
            val cy = output[1][i]
            val w  = output[2][i]
            val h  = output[3][i]

            // Find best class
            var maxConf = 0f
            var maxIdx = 0
            for (c in 0 until NUM_CLASSES) {
                val conf = output[4 + c][i]
                if (conf > maxConf) {
                    maxConf = conf
                    maxIdx = c
                }
            }

            if (maxConf >= CONF_THRESHOLD) {
                // Convert to normalized coords
                val x1 = (cx - w / 2f) / INPUT_SIZE
                val y1 = (cy - h / 2f) / INPUT_SIZE
                val x2 = (cx + w / 2f) / INPUT_SIZE
                val y2 = (cy + h / 2f) / INPUT_SIZE

                detections.add(
                    Detection(
                        label = COCO_LABELS.getOrElse(maxIdx) { "object" },
                        confidence = maxConf,
                        boundingBox = RectF(
                            x1.coerceIn(0f, 1f),
                            y1.coerceIn(0f, 1f),
                            x2.coerceIn(0f, 1f),
                            y2.coerceIn(0f, 1f)
                        )
                    )
                )
            }
        }

        return applyNMS(detections)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)
            sorted.removeAll { iou(best.boundingBox, it.boundingBox) > IOU_THRESHOLD }
        }

        return result
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft   = maxOf(a.left, b.left)
        val interTop    = maxOf(a.top, b.top)
        val interRight  = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        if (interRight <= interLeft || interBottom <= interTop) return 0f

        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val aArea = a.width() * a.height()
        val bArea = b.width() * b.height()

        return interArea / (aArea + bArea - interArea)
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
    }
}
