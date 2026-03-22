package com.objectcounter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.objectcounter.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var detector: ObjectDetector
    private var currentBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri -> loadImageFromUri(uri) }
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            currentBitmap = it
            binding.imageView.setImageBitmap(it)
            binding.btnDetect.isEnabled = true
            binding.resultCard.visibility = View.GONE
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            openCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = ObjectDetector(this)

        setupUI()
    }

    private fun setupUI() {
        binding.btnCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        }

        binding.btnGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        binding.btnDetect.setOnClickListener {
            currentBitmap?.let { detectObjects(it) }
        }

        binding.btnReset.setOnClickListener {
            resetUI()
        }
    }

    private fun openCamera() {
        cameraLauncher.launch(null)
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            currentBitmap = bitmap
            binding.imageView.setImageBitmap(bitmap)
            binding.btnDetect.isEnabled = true
            binding.resultCard.visibility = View.GONE
            binding.placeholderText.visibility = View.GONE
        } catch (e: Exception) {
            Toast.makeText(this, "Image load කරන්න බැරි වුණා", Toast.LENGTH_SHORT).show()
        }
    }

    private fun detectObjects(bitmap: Bitmap) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnDetect.isEnabled = false
        binding.scanningOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.Default) {
                    detector.detect(bitmap)
                }
                displayResults(bitmap, results)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity,
                    "Detection error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnDetect.isEnabled = true
                binding.scanningOverlay.visibility = View.GONE
            }
        }
    }

    private fun displayResults(bitmap: Bitmap, detections: List<Detection>) {
        // Draw bounding boxes
        val resultBitmap = drawBoundingBoxes(bitmap.copy(Bitmap.Config.ARGB_8888, true), detections)
        binding.imageView.setImageBitmap(resultBitmap)

        // Count by class
        val countMap = mutableMapOf<String, Int>()
        detections.forEach { det ->
            countMap[det.label] = (countMap[det.label] ?: 0) + 1
        }

        // Update UI
        binding.totalCount.text = detections.size.toString()
        binding.resultCard.visibility = View.VISIBLE

        // Build object list text
        val sb = StringBuilder()
        countMap.entries.sortedByDescending { it.value }.forEach { (label, count) ->
            sb.append("• $label: $count\n")
        }
        binding.objectList.text = sb.toString().trimEnd()

        // Animate count
        animateCount(detections.size)
    }

    private fun drawBoundingBoxes(bitmap: Bitmap, detections: List<Detection>): Bitmap {
        val canvas = Canvas(bitmap)
        val colors = listOf(
            Color.parseColor("#00FF88"), Color.parseColor("#FF6B35"),
            Color.parseColor("#4FC3F7"), Color.parseColor("#FF4081"),
            Color.parseColor("#FFEB3B"), Color.parseColor("#CE93D8"),
            Color.parseColor("#80CBC4"), Color.parseColor("#F48FB1")
        )

        val classColorMap = mutableMapOf<String, Int>()
        var colorIndex = 0

        detections.forEach { det ->
            val color = classColorMap.getOrPut(det.label) {
                colors[colorIndex++ % colors.size]
            }

            val boxPaint = Paint().apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = bitmap.width * 0.004f
            }

            val bgPaint = Paint().apply {
                this.color = color
                style = Paint.Style.FILL
                alpha = 200
            }

            val textPaint = Paint().apply {
                this.color = Color.BLACK
                textSize = bitmap.width * 0.030f
                typeface = Typeface.DEFAULT_BOLD
            }

            val left = det.boundingBox.left * bitmap.width
            val top = det.boundingBox.top * bitmap.height
            val right = det.boundingBox.right * bitmap.width
            val bottom = det.boundingBox.bottom * bitmap.height

            // Draw box
            canvas.drawRect(left, top, right, bottom, boxPaint)

            // Draw label background
            val labelText = "${det.label} ${(det.confidence * 100).toInt()}%"
            val textWidth = textPaint.measureText(labelText)
            val textHeight = textPaint.textSize
            val labelTop = if (top > textHeight + 8) top - textHeight - 8 else top
            canvas.drawRect(left, labelTop, left + textWidth + 12, labelTop + textHeight + 8, bgPaint)

            // Draw label text
            canvas.drawText(labelText, left + 6, labelTop + textHeight + 2, textPaint)
        }

        return bitmap
    }

    private fun animateCount(target: Int) {
        var current = 0
        val handler = android.os.Handler(mainLooper)
        val step = if (target > 20) target / 20 else 1

        val runnable = object : Runnable {
            override fun run() {
                current = minOf(current + step, target)
                binding.totalCount.text = current.toString()
                if (current < target) {
                    handler.postDelayed(this, 40)
                }
            }
        }
        handler.post(runnable)
    }

    private fun resetUI() {
        currentBitmap = null
        binding.imageView.setImageResource(android.R.color.transparent)
        binding.placeholderText.visibility = View.VISIBLE
        binding.btnDetect.isEnabled = false
        binding.resultCard.visibility = View.GONE
        binding.totalCount.text = "0"
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.close()
    }
}
