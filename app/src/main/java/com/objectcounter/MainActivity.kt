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
import java.io.File

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
            binding.btnDetect.isEnabled = detector.isModelLoaded
            binding.resultCard.visibility = View.GONE
            binding.placeholderText.visibility = View.GONE
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) openCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = ObjectDetector(this)

        // Check if model needs downloading
        if (!detector.isModelLoaded) {
            showModelDownloadUI()
        }

        setupUI()
    }

    private fun showModelDownloadUI() {
        binding.placeholderText.text = "⬇️ YOLOv8 model download කරනවා...\nInternet connection ඕනේ (6MB)"
        binding.btnDetect.isEnabled = false

        lifecycleScope.launch {
            val success = ModelDownloader.downloadModel(this@MainActivity) { progress ->
                binding.placeholderText.text = "⬇️ Model downloading... $progress%"
            }
            if (success) {
                detector.reloadModel()
                if (detector.isModelLoaded) {
                    binding.placeholderText.text = "✅ Model ready!\n📷 Camera හෝ Gallery එකෙන් image select කරන්න"
                    if (currentBitmap != null) binding.btnDetect.isEnabled = true
                    Toast.makeText(this@MainActivity, "Model loaded!", Toast.LENGTH_SHORT).show()
                } else {
                    binding.placeholderText.text = "❌ Model load failed. App restart කරන්න."
                }
            } else {
                binding.placeholderText.text = "❌ Download failed.\nInternet check කරලා app restart කරන්න."
            }
        }
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
            if (!detector.isModelLoaded) {
                Toast.makeText(this, "Model still loading...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentBitmap?.let { detectObjects(it) }
        }

        binding.btnReset.setOnClickListener { resetUI() }
    }

    private fun openCamera() { cameraLauncher.launch(null) }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            currentBitmap = bitmap
            binding.imageView.setImageBitmap(bitmap)
            binding.btnDetect.isEnabled = detector.isModelLoaded
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
                val results = withContext(Dispatchers.Default) { detector.detect(bitmap) }
                displayResults(bitmap, results)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Detection error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnDetect.isEnabled = true
                binding.scanningOverlay.visibility = View.GONE
            }
        }
    }

    private fun displayResults(bitmap: Bitmap, detections: List<Detection>) {
        val resultBitmap = drawBoundingBoxes(bitmap.copy(Bitmap.Config.ARGB_8888, true), detections)
        binding.imageView.setImageBitmap(resultBitmap)

        val countMap = mutableMapOf<String, Int>()
        detections.forEach { countMap[it.label] = (countMap[it.label] ?: 0) + 1 }

        animateCount(detections.size)
        binding.resultCard.visibility = View.VISIBLE

        val sb = StringBuilder()
        countMap.entries.sortedByDescending { it.value }.forEach { (label, count) ->
            sb.append("• $label: $count\n")
        }
        binding.objectList.text = if (sb.isEmpty()) "Objects not detected" else sb.toString().trimEnd()
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
            val color = classColorMap.getOrPut(det.label) { colors[colorIndex++ % colors.size] }
            val boxPaint = Paint().apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = bitmap.width * 0.004f }
            val bgPaint = Paint().apply { this.color = color; style = Paint.Style.FILL; alpha = 200 }
            val textPaint = Paint().apply { this.color = Color.BLACK; textSize = bitmap.width * 0.030f; typeface = Typeface.DEFAULT_BOLD }

            val left = det.boundingBox.left * bitmap.width
            val top = det.boundingBox.top * bitmap.height
            val right = det.boundingBox.right * bitmap.width
            val bottom = det.boundingBox.bottom * bitmap.height

            canvas.drawRect(left, top, right, bottom, boxPaint)
            val labelText = "${det.label} ${(det.confidence * 100).toInt()}%"
            val tw = textPaint.measureText(labelText)
            val th = textPaint.textSize
            val ly = if (top > th + 8) top - th - 8 else top
            canvas.drawRect(left, ly, left + tw + 12, ly + th + 8, bgPaint)
            textPaint.color = Color.BLACK
            canvas.drawText(labelText, left + 6, ly + th + 2, textPaint)
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
                if (current < target) handler.postDelayed(this, 40)
            }
        }
        handler.post(runnable)
    }

    private fun resetUI() {
        currentBitmap = null
        binding.imageView.setImageResource(android.R.color.transparent)
        binding.placeholderText.visibility = View.VISIBLE
        binding.placeholderText.text = "📷 Camera හෝ Gallery\nඑකෙන් image select කරන්න"
        binding.btnDetect.isEnabled = false
        binding.resultCard.visibility = View.GONE
        binding.totalCount.text = "0"
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.close()
    }
}
