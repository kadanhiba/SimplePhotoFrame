package com.hiba.simplephotoframe

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewAnimator
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import android.provider.DocumentsContract
import android.util.Log
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class SlideshowActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var settings: SlideshowSettings
    private var mediaList = mutableListOf<Uri>()
    private var currentIndex = -1
    private var history = mutableListOf<Int>()
    private var historyIndex = -1

    private lateinit var viewAnimator: ViewAnimator
    private lateinit var imageView1: ImageView
    private lateinit var imageView2: ImageView
    private lateinit var playerView: PlayerView
    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvFileName: TextView
    private lateinit var clockContainer: View
    private var exoPlayer: ExoPlayer? = null
    private var currentPositionIndex = 0 // 0: Top-Right, 1: Top-Left, 2: Bottom-Left, 3: Bottom-Right
    private var videoStartedWithLoadId = -1
    private var currentLoadId = 0

    private val handler = Handler(Looper.getMainLooper())
    private val clockHandler = Handler(Looper.getMainLooper())

    private val nextRunnable = Runnable {
        Log.d("Slideshow", "nextRunnable: Watchdog/Transition triggered! Calling showNext()")
        showNext()
    }
    private val clockRunnable = object : Runnable {
        override fun run() {
            if (settings.useSchedule) {
                checkSchedule()
            }
            updateClock()
            clockHandler.postDelayed(this, 1000)
        }
    }

    private fun checkSchedule() {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTimeStr = sdf.format(Date())
        
        val isVisible = if (settings.useSchedule) {
            isTimeBetween(currentTimeStr, settings.startTime, settings.endTime)
        } else {
            true
        }
        
        val newVisibility = if (isVisible) View.VISIBLE else View.GONE
        if (viewAnimator.visibility != newVisibility) {
            viewAnimator.visibility = newVisibility
            clockContainer.visibility = newVisibility
            
            if (!isVisible) {
                Toast.makeText(this, "Display schedule: Sleep mode", Toast.LENGTH_SHORT).show()
                handler.removeCallbacks(nextRunnable)
                exoPlayer?.pause()
            } else {
                Toast.makeText(this, "Display schedule: Active mode", Toast.LENGTH_SHORT).show()
                showNext()
            }
        }
    }

    private fun isTimeBetween(current: String, start: String, end: String): Boolean {
        return if (start <= end) {
            current in start..end
        } else {
            // Over midnight schedule (e.g., 22:00 to 06:00)
            current >= start || current <= end
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slideshow)

        hideSystemUI()

        settingsManager = SettingsManager(this)
        settings = settingsManager.getSettings()

        viewAnimator = findViewById(R.id.viewAnimator)
        imageView1 = findViewById(R.id.imageView1)
        imageView2 = findViewById(R.id.imageView2)
        playerView = findViewById(R.id.playerView)
        tvClock = findViewById(R.id.tvClock)
        tvDate = findViewById(R.id.tvDate)
        tvFileName = findViewById(R.id.tvFileName)
        clockContainer = findViewById(R.id.clockContainer)

        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (settings.showClock || settings.dateDisplay != "None" || settings.useSchedule) {
            if (!settings.preventBurnIn) {
                // Set initial static position
                currentPositionIndex = when (settings.clockLocation) {
                    "Top-Right" -> 0
                    "Top-Left" -> 1
                    "Bottom-Left" -> 2
                    "Bottom-Right" -> 3
                    else -> 0
                }
                updateClockPosition()
            }
            clockHandler.post(clockRunnable)
        }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(vx) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX < 0) {
                            // Swipe Left -> Next
                            showNext()
                        } else {
                            // Swipe Right -> Previous
                            showPrevious()
                        }
                        return true
                    }
                }
                return false
            }
        })

        viewAnimator.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        setupTransitions()
        
        // Force visibility initially
        viewAnimator.visibility = View.VISIBLE
        clockContainer.visibility = View.VISIBLE
        
        loadMedia()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun setupTransitions() {
        val transition = if (settings.transition == "Random") {
            arrayOf("Fade", "Swap Right", "Swap Left", "Slide Up", "Slide Down", "Zoom").random()
        } else {
            settings.transition
        }

        val anim = when (transition) {
            "Fade" -> {
                viewAnimator.inAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
                viewAnimator.outAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
                listOf(viewAnimator.inAnimation, viewAnimator.outAnimation)
            }
            "Swap Right" -> {
                viewAnimator.inAnimation = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
                viewAnimator.outAnimation = AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right)
                listOf(viewAnimator.inAnimation, viewAnimator.outAnimation)
            }
            "Swap Left" -> {
                viewAnimator.inAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
                viewAnimator.outAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
                listOf(viewAnimator.inAnimation, viewAnimator.outAnimation)
            }
            "Slide Up" -> {
                viewAnimator.inAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom)
                viewAnimator.outAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_out_top)
                listOf(viewAnimator.inAnimation, viewAnimator.outAnimation)
            }
            "Slide Down" -> {
                viewAnimator.inAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_in_top)
                viewAnimator.outAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_out_bottom)
                listOf(viewAnimator.inAnimation, viewAnimator.outAnimation)
            }
            "Zoom" -> {
                viewAnimator.inAnimation = AnimationUtils.loadAnimation(this, R.anim.zoom_in)
                viewAnimator.outAnimation = AnimationUtils.loadAnimation(this, R.anim.zoom_out)
                listOf(viewAnimator.inAnimation, viewAnimator.outAnimation)
            }
            else -> {
                viewAnimator.inAnimation = null
                viewAnimator.outAnimation = null
                emptyList()
            }
        }

        val durationMs = (settings.transitionDurationSeconds * 1000).toLong()
        anim.forEach { it?.duration = durationMs }
    }

    private fun loadMedia() {
        val folderUris = settingsManager.getFolders()
        if (folderUris.isEmpty()) {
            Toast.makeText(this, "No folders selected. Please add folders in settings.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        mediaList.clear()
        Toast.makeText(this, "Scanning media folders...", Toast.LENGTH_SHORT).show()

        Thread {
            val batch = mutableListOf<Uri>()
            var lastUpdateTime = System.currentTimeMillis()

            for (uriString in folderUris) {
                try {
                    val rootUri = Uri.parse(uriString)
                    Log.d("Slideshow", "Scanning root: $rootUri")
                    scanDirectoryFast(rootUri) { uri ->
                        synchronized(batch) {
                            batch.add(uri)
                        }
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime > 1000 || batch.size >= 50) {
                            val toAdd = synchronized(batch) {
                                val temp = batch.toList()
                                batch.clear()
                                temp
                            }
                            lastUpdateTime = now
                            runOnUiThread {
                                val wasEmpty = mediaList.isEmpty()
                                mediaList.addAll(toAdd)
                                Log.d("Slideshow", "Added ${toAdd.size} items. Total: ${mediaList.size}")
                                if (wasEmpty && mediaList.isNotEmpty()) {
                                    startSlideshow()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Slideshow", "Error scanning $uriString", e)
                }
            }

            // Final batch
            val finalToAdd = synchronized(batch) {
                val temp = batch.toList()
                batch.clear()
                temp
            }
            runOnUiThread {
                val wasEmpty = mediaList.isEmpty()
                mediaList.addAll(finalToAdd)
                Log.d("Slideshow", "Final batch: ${finalToAdd.size}. Total: ${mediaList.size}")
                
                if (wasEmpty && mediaList.isNotEmpty()) {
                    startSlideshow()
                }
                
                if (mediaList.isEmpty()) {
                    Toast.makeText(this@SlideshowActivity, "No photos or videos found.", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    if (settings.order == "randomized") {
                        mediaList.shuffle()
                        Log.d("Slideshow", "List shuffled")
                    }
                    Toast.makeText(this@SlideshowActivity, "Scan complete: ${mediaList.size} items.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun scanDirectoryFast(rootUri: Uri, onFileFound: (Uri) -> Unit) {
        val stack = mutableListOf<String>()
        try {
            stack.add(DocumentsContract.getTreeDocumentId(rootUri))
        } catch (e: Exception) {
            Log.e("Slideshow", "Failed to get tree ID for $rootUri", e)
            return
        }

        while (stack.isNotEmpty()) {
            val docId = stack.removeAt(stack.size - 1)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, docId)
            
            val cursor = contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                while (it.moveToNext()) {
                    val childId = it.getString(idCol)
                    val mimeType = it.getString(mimeCol)
                    val name = it.getString(nameCol) ?: ""

                    if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                        stack.add(childId)
                    } else {
                        if (mimeType.startsWith("image/") || mimeType.startsWith("video/") ||
                            isMediaExtension(name)) {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, childId)
                            onFileFound(fileUri)
                        }
                    }
                }
            }
        }
    }

    private fun isMediaExtension(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".mp4") ||
                lower.endsWith(".mkv") || lower.endsWith(".mov") ||
                lower.endsWith(".webp")
    }

    private fun startSlideshow() {
        Log.d("Slideshow", "startSlideshow called, mediaList size: ${mediaList.size}")
        if (mediaList.isNotEmpty()) {
            if (currentIndex == -1) {
                currentIndex = 0
                displayMedia(mediaList[currentIndex])
            }
        } else {
            Log.w("Slideshow", "startSlideshow: mediaList is empty")
            Toast.makeText(this, "No photos or videos found in the selected folders.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun showNext() {
        handler.removeCallbacks(nextRunnable)
        if (mediaList.isEmpty()) {
            Log.w("Slideshow", "showNext: mediaList is empty")
            return
        }

        // 1. Update our index
        currentIndex++
        if (currentIndex >= mediaList.size) {
            currentIndex = 0
            if (settings.order == "randomized") mediaList.shuffle()
        }
        Log.d("Slideshow", "showNext: currentIndex = $currentIndex, total = ${mediaList.size}")
        
        // 2. Track history
        history.add(currentIndex)
        if (history.size > 50) history.removeAt(0)
        historyIndex = history.size - 1

        // 3. Display the media
        displayMedia(mediaList[currentIndex])
    }

    private fun showPrevious() {
        handler.removeCallbacks(nextRunnable)
        if (mediaList.isEmpty() || historyIndex <= 0) {
            Log.w("Slideshow", "showPrevious: Cannot go back. historyIndex = $historyIndex")
            return
        }

        // 1. Go back in history
        historyIndex--
        currentIndex = history[historyIndex]
        Log.d("Slideshow", "showPrevious: currentIndex = $currentIndex, historyIndex = $historyIndex")

        // 2. Apply transitions
        setupTransitions()

        // 3. Display the media
        displayMedia(mediaList[currentIndex])
    }

    private fun displayMedia(uri: Uri) {
        Log.d("Slideshow", "displayMedia: $uri")
        setupTransitions()
        
        val mimeType = try {
            contentResolver.getType(uri) ?: ""
        } catch (e: Exception) {
            ""
        }

        if (mimeType.startsWith("video/") || uri.toString().lowercase().endsWith(".mp4") || uri.toString().lowercase().endsWith(".mkv")) {
            updateFileNameDisplay(uri, "Video")
            showVideo(uri)
        } else {
            updateFileNameDisplay(uri, "Loading...")
            showImage(uri)
        }
    }

    private fun updateFileNameDisplay(uri: Uri, status: String = "") {
        if (!settings.developerMode) {
            tvFileName.visibility = View.GONE
            return
        }
        var fileName = "Unknown"
        try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        fileName = it.getString(displayNameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            fileName = uri.path?.substringAfterLast('/') ?: "Unknown"
        }
        
        tvFileName.text = if (status.isEmpty()) fileName else "$fileName ($status)"
        tvFileName.visibility = View.VISIBLE
    }

    private fun initTransitions() {
        viewAnimator.setInAnimation(this, R.anim.zoom_in)
        viewAnimator.setOutAnimation(this, R.anim.zoom_out)
    }

    private fun showImage(uri: Uri) {
        val loadId = ++currentLoadId
        Log.d("Slideshow", "showImage: $uri, loadId: $loadId")
        
        val currentChild = viewAnimator.displayedChild
        val nextIndex = if (currentChild == 0) 1 else 0
        val targetImageView = if (nextIndex == 1) imageView2 else imageView1

        // Ensure the view is NOT 'GONE' so Glide can measure it immediately.
        // We use INVISIBLE so it doesn't pop over the current image before it's ready.
        targetImageView.visibility = View.INVISIBLE

        handler.removeCallbacks(nextRunnable)
        
        // Safety watchdog: if image doesn't load in 15s, skip it
        Log.d("Slideshow", "Setting 15s watchdog for loadId: $loadId")
        handler.postDelayed(nextRunnable, 15000L)

        val metrics = resources.displayMetrics

        Glide.with(this)
            .load(uri)
            .override(metrics.widthPixels, metrics.heightPixels) // Avoid hang on GONE views
            .timeout(15000)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerInside()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    if (loadId == currentLoadId) {
                        Log.e("Slideshow", "Glide load failed for $uri", e)
                        updateFileNameDisplay(uri, "Load Failed")
                        handler.postDelayed({ showNext() }, 2000)
                    }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d("Slideshow", "onResourceReady: $uri, loadId: $loadId. DataSource: $dataSource")
                    if (loadId == currentLoadId) {
                        handler.post {
                            handler.removeCallbacks(nextRunnable)
                            updateFileNameDisplay(uri, "Ready")
                            
                            exoPlayer?.stop()
                            Log.d("Slideshow", "Switching ViewAnimator to child: $nextIndex")
                            
                            // Ensure the target ImageView is actually updated BEFORE switching
                            // Glide handles this internally, but sometimes ViewAnimator needs a push
                            viewAnimator.displayedChild = nextIndex
                            
                            val delay = (settings.durationSeconds.coerceAtLeast(1)) * 1000L
                            Log.d("Slideshow", "Image displayed. Next slide in ${settings.durationSeconds}s")
                            handler.postDelayed(nextRunnable, delay)
                        }
                    } else {
                        Log.w("Slideshow", "onResourceReady ignored: loadId mismatch (Got $loadId, current $currentLoadId)")
                    }
                    return false
                }
            })
            .into(targetImageView)
    }

    private fun showVideo(uri: Uri) {
        val loadId = ++currentLoadId
        videoStartedWithLoadId = loadId
        Log.d("Slideshow", "showVideo: $uri, loadId: $loadId")
        
        // Preparation watchdog: skip if video doesn't start in 30s
        handler.removeCallbacks(nextRunnable)
        handler.postDelayed(nextRunnable, 30000L)

        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(this).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (videoStartedWithLoadId == currentLoadId) {
                            when (state) {
                                Player.STATE_READY -> {
                                    Log.d("Slideshow", "Video ready: $uri. Removing prep watchdog.")
                                    handler.removeCallbacks(nextRunnable)
                                    // Safety fallback for playback hang (30 mins max)
                                    handler.postDelayed(nextRunnable, 1800000L)
                                }
                                Player.STATE_ENDED -> {
                                    Log.d("Slideshow", "Video ended: $uri")
                                    showNext()
                                }
                                Player.STATE_BUFFERING -> Log.d("Slideshow", "Video buffering: $uri")
                                else -> {}
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (videoStartedWithLoadId == currentLoadId) {
                            Log.e("Slideshow", "ExoPlayer error: ${error.errorCodeName} - ${error.message}", error)
                            showNext()
                        }
                    }
                })
            }
            playerView.player = exoPlayer
        }

        exoPlayer?.apply {
            stop()
            clearMediaItems()
            volume = if (settings.autoMuteVideo) 0f else 1f
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            play()
        }

        // Switch to the PlayerView (index 2)
        Log.d("Slideshow", "Switching to child: 2 (Video)")
        viewAnimator.displayedChild = 2
    }

    private fun updateClock() {
        val now = Date()
        val calendar = Calendar.getInstance()
        calendar.time = now

        if (settings.preventBurnIn) {
            val minute = calendar.get(Calendar.MINUTE)
            val newPositionIndex = minute % 4
            if (newPositionIndex != currentPositionIndex) {
                currentPositionIndex = newPositionIndex
                updateClockPosition()
            }
        }
        
        if (settings.showClock) {
            val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvClock.text = timeSdf.format(now)
            tvClock.visibility = View.VISIBLE
        } else {
            tvClock.visibility = View.GONE
        }

        if (settings.dateDisplay != "None") {
            val pattern = when (settings.dateDisplay) {
                "DD/MM" -> "dd/MM"
                "DD/MM/YEAR" -> "dd/MM/yyyy"
                else -> null
            }
            
            pattern?.let {
                val dateSdf = SimpleDateFormat(it, Locale.getDefault())
                tvDate.text = dateSdf.format(now)
                tvDate.visibility = View.VISIBLE
            }
        } else {
            tvDate.visibility = View.GONE
        }
    }

    private fun updateClockPosition() {
        val params = clockContainer.layoutParams as android.widget.FrameLayout.LayoutParams
        val container = clockContainer as android.widget.LinearLayout
        
        when (currentPositionIndex) {
            0 -> { // Top-Right
                params.gravity = android.view.Gravity.TOP or android.view.Gravity.END
                container.gravity = android.view.Gravity.END
            }
            1 -> { // Top-Left
                params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
                container.gravity = android.view.Gravity.START
            }
            2 -> { // Bottom-Left
                params.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
                container.gravity = android.view.Gravity.START
            }
            3 -> { // Bottom-Right
                params.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                container.gravity = android.view.Gravity.END
            }
        }
        clockContainer.layoutParams = params
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(nextRunnable)
        clockHandler.removeCallbacks(clockRunnable)
        exoPlayer?.release()
    }
}
