# 🔍 ObjectLens — AI Object Counter

YOLOv8n model use කරලා offline object detect කරන Android app එකක්. API නැතිව, 100% on-device inference.

![Android](https://img.shields.io/badge/Android-24%2B-green)
![YOLOv8](https://img.shields.io/badge/Model-YOLOv8n-blue)
![TFLite](https://img.shields.io/badge/Runtime-TFLite-orange)

---

## ✨ Features

- 📷 Camera හෝ Gallery එකෙන් image select කරන්න
- 🧠 YOLOv8n TensorFlow Lite model (80 COCO classes)
- 📦 Bounding boxes + labels real-time draw කරනවා
- 🔢 Total object count animated display
- 📋 Per-class count list
- ⚡ GPU acceleration (supported devices)
- 🔒 100% offline — no internet needed after first run

---

## 📱 APK Download

**GitHub Actions** automatically build කරනවා:

1. `Actions` tab එකට යන්න
2. Latest workflow run click කරන්න
3. **Artifacts** section එකෙන් `ObjectLens-debug` download කරන්න

---

## 🚀 GitHub එකට Upload කරන විදිය

### Step 1 — New Repository
```
github.com → New repository → "ObjectLens" → Create
```

### Step 2 — Files Upload
```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/ObjectLens.git
git push -u origin main
```

### Step 3 — APK Build
Push කළාම GitHub Actions automatically start වෙනවා (~5-8 minutes).

`Actions` → latest run → `Artifacts` → APK download.

---

## 🏗️ Project Structure

```
ObjectLens/
├── app/src/main/
│   ├── java/com/objectcounter/
│   │   ├── MainActivity.kt        # UI + camera/gallery
│   │   ├── ObjectDetector.kt      # YOLOv8 TFLite inference
│   │   ├── ModelDownloader.kt     # First-run model download
│   │   └── SplashActivity.kt      # Splash + model check
│   ├── res/layout/
│   │   ├── activity_main.xml
│   │   └── activity_splash.xml
│   └── assets/                    # yolov8n.tflite (auto-downloaded by CI)
├── .github/workflows/
│   └── build.yml                  # Auto APK build
└── README.md
```

---

## 🧠 Model Info

| Property | Value |
|----------|-------|
| Model | YOLOv8n (nano) |
| Format | TensorFlow Lite Float32 |
| Input | 640×640 RGB |
| Classes | 80 (COCO dataset) |
| Size | ~6MB |
| Inference | ~50-150ms (device dependent) |

### Detected Classes (80)
person, bicycle, car, motorcycle, airplane, bus, train, truck, boat,
traffic light, fire hydrant, stop sign, bench, bird, cat, dog, horse,
sheep, cow, elephant, bear, zebra, giraffe, backpack, umbrella, handbag,
bottle, wine glass, cup, fork, knife, spoon, bowl, banana, apple,
sandwich, orange, broccoli, carrot, hot dog, pizza, donut, cake, chair,
couch, potted plant, bed, dining table, toilet, tv, laptop, mouse,
remote, keyboard, cell phone, microwave, oven, toaster, sink,
refrigerator, book, clock, vase, scissors, teddy bear, hair drier, toothbrush

---

## 🛠️ Local Build (Optional)

Android Studio තිබේ නම්:
```bash
git clone https://github.com/YOUR_USERNAME/ObjectLens.git
cd ObjectLens

# Model download
curl -L "https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n_float32.tflite" \
  -o app/src/main/assets/yolov8n.tflite

# Build
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 📋 Requirements

- Android 7.0+ (API 24+)
- ~50MB storage (model included)
- Camera permission (optional)

---

## 📄 License
MIT License
