package com.bili.biliplus

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.media.AudioManager
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.appbar.MaterialToolbar
import android.widget.EditText
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.Random
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var viewCountText: TextView
    private lateinit var viewDeltaText: TextView
    private lateinit var statusText: TextView
    private lateinit var touchInterceptor: View
    private lateinit var toolbar: MaterialToolbar

    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var isRunning = false
    private var initialViewCount: Long = -1
    private var lastViewCount: Long = 0
    private var currentAid: Long = -1
    private var refreshCount = 0
    private var videoUrl: String = ""

    private var service: ViewCountService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as ViewCountService.LocalBinder
            service = localBinder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            serviceBound = false
        }
    }

    private val PC_USER_AGENTS = arrayOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Edg/122.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36 Edg/121.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36 QQBrowser/10.7.4310.400",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36 360SE"
    )

    private var cycleRunning = false

    private val afterLoadRunnable = Runnable {
        if (!cycleRunning) return@Runnable
        statusText.text = getString(R.string.status_running) + " | 页面停留中…"
        handler.postDelayed(afterStayRunnable, 30_000)
    }

    private val afterStayRunnable = Runnable {
        if (!cycleRunning) return@Runnable
        fetchViewCount()
        webView.loadUrl("file:///android_asset/cooldown.html")
        CookieManager.getInstance().removeAllCookies(null)
        webView.clearCache(true)
        statusText.text = getString(R.string.status_running) + " | 冷却中…"
        handler.postDelayed(afterCooldownRunnable, 30_000)
    }

    private val afterCooldownRunnable = Runnable {
        if (!cycleRunning) return@Runnable
        nextCycle()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "通知权限被拒绝，可能影响保活", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermission()
        initViews()
        setupToolbar()
        setupWebView()
        setupButtons()
    }

    override fun onDestroy() {
        stopRunning()
        unbindServiceSafe()
        webView.destroy()
        super.onDestroy()
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        viewCountText = findViewById(R.id.viewCountText)
        viewDeltaText = findViewById(R.id.viewDeltaText)
        statusText = findViewById(R.id.statusText)
        touchInterceptor = findViewById(R.id.touchInterceptor)
        toolbar = findViewById(R.id.toolbar)

        touchInterceptor.setOnTouchListener { _, _ -> true }
    }

    private fun setupToolbar() {
        toolbar.overflowIcon?.setTint(0xFFFFFFFF.toInt())
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_battery -> {
                    requestIgnoreBatteryOptimization()
                    true
                }
                R.id.menu_about -> {
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupWebView() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.setStreamMute(AudioManager.STREAM_MUSIC, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            userAgentString = getRandomUA()
            setSupportMultipleWindows(false)
            allowFileAccess = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            removeAllCookies(null)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(
                    "document.querySelectorAll('video,audio').forEach(e=>{e.muted=true;e.volume=0});",
                    null
                )
                injectScrollSimulation()
                if (isRunning) {
                    simulateHumanBehavior()
                }
            }
        }
    }

    private fun setupButtons() {
        startButton.setOnClickListener { startRunning() }
        stopButton.setOnClickListener { stopRunning() }
    }

    private fun startRunning() {
        val inputUrl = urlInput.text?.toString()?.trim() ?: ""
        if (inputUrl.isEmpty()) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
            return
        }

        isRunning = true
        cycleRunning = true
        refreshCount = 0
        startButton.isEnabled = false
        stopButton.isEnabled = true
        statusText.text = getString(R.string.status_running)

        val intent = Intent(this, ViewCountService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        currentAid = BvAvConverter.extractAvId(inputUrl) ?: -1
        fetchViewCount()
        statusText.text = getString(R.string.status_running) + " | 首次获取播放量完成"
        resolveAndLoad(inputUrl)
        handler.postDelayed(afterLoadRunnable, 30_000)
    }

    private fun stopRunning() {
        isRunning = false
        cycleRunning = false
        handler.removeCallbacks(afterLoadRunnable)
        handler.removeCallbacks(afterStayRunnable)
        handler.removeCallbacks(afterCooldownRunnable)

        startButton.isEnabled = true
        stopButton.isEnabled = false
        statusText.text = getString(R.string.status_idle)

        webView.loadUrl("about:blank")

        if (serviceBound) {
            stopService(Intent(this, ViewCountService::class.java))
            unbindServiceSafe()
        }
    }

    private fun unbindServiceSafe() {
        if (serviceBound) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) {}
            serviceBound = false
            service = null
        }
    }

    private fun resolveAndLoad(url: String) {
        if (url.contains("b23.tv")) {
            statusText.text = "解析短链接…"
            resolveShortUrl(url) { resolvedUrl ->
                if (resolvedUrl != null) {
                    runOnUiThread {
                        videoUrl = resolvedUrl
                        currentAid = BvAvConverter.extractAvId(resolvedUrl) ?: -1
                        loadVideo(resolvedUrl)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, R.string.error_resolve, Toast.LENGTH_SHORT).show()
                        stopRunning()
                    }
                }
            }
        } else {
            videoUrl = url
            currentAid = BvAvConverter.extractAvId(url) ?: -1
            loadVideo(url)
        }
    }

    private fun resolveShortUrl(shortUrl: String, callback: (String?) -> Unit) {
        val request = Request.Builder()
            .url(shortUrl)
            .header("User-Agent", getRandomUA())
            .head()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val location = response.header("Location")
                callback(location)
                response.close()
            }
        })
    }

    private fun loadVideo(url: String) {
        webView.settings.userAgentString = getRandomUA()
        CookieManager.getInstance().removeAllCookies(null)
        webView.clearCache(true)
        webView.loadUrl(url)
    }

    private fun refreshPage() {
        refreshCount++
        if (videoUrl.isEmpty()) return
        webView.settings.userAgentString = getRandomUA()
        CookieManager.getInstance().removeAllCookies(null)
        webView.clearCache(true)
        webView.loadUrl(videoUrl)
    }

    private fun nextCycle() {
        if (!cycleRunning) return
        refreshPage()
        statusText.text = getString(R.string.status_running) + " | 刷新 #${refreshCount}"
        handler.postDelayed(afterLoadRunnable, 30_000)
    }

    private fun fetchViewCount() {
        if (currentAid <= 0) {
            val inputUrl = urlInput.text?.toString()?.trim() ?: ""
            currentAid = BvAvConverter.extractAvId(inputUrl) ?: -1
        }
        if (currentAid <= 0) return

        val apiUrl = "https://api.bilibili.com/x/web-interface/view?aid=$currentAid"
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", getRandomUA())
            .header("Referer", "https://www.bilibili.com")
            .header("Accept", "application/json, text/plain, */*")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: return
                    val json = org.json.JSONObject(body)
                    if (json.getInt("code") == 0) {
                        val data = json.getJSONObject("data")
                        val stat = data.getJSONObject("stat")
                        val viewCount = stat.getLong("view")

                        runOnUiThread {
                            if (initialViewCount < 0) {
                                initialViewCount = viewCount
                            }
                            lastViewCount = viewCount
                            val delta = viewCount - initialViewCount

                            viewCountText.text = "播放量: ${formatNumber(viewCount)}"
                            viewDeltaText.text = "Δ ${formatNumber(delta)}"
                            viewDeltaText.setTextColor(
                                if (delta >= 0) getColor(R.color.green)
                                else getColor(R.color.red)
                            )

                            service?.updateNotification(viewCount, delta)
                        }
                    }
                } catch (_: Exception) {}
                response.close()
            }
        })
    }

    private fun injectScrollSimulation() {
        val js = """
            (function() {
                var scrollY = Math.floor(Math.random() * 600) + 200;
                window.scrollBy(0, scrollY);
                setTimeout(function() {
                    window.scrollBy(0, -Math.floor(scrollY / 3));
                }, 2000);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun simulateHumanBehavior() {
        handler.postDelayed({
            if (!isRunning) return@postDelayed
            injectScrollSimulation()
        }, 5000)
    }

    private fun getRandomUA(): String {
        return PC_USER_AGENTS[random.nextInt(PC_USER_AGENTS.size)]
    }

    private fun formatNumber(n: Long): String {
        return String.format(Locale.US, "%,d", n)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
            } else {
                Toast.makeText(this, "已忽略电池优化", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this, R.style.Theme_BiliPlus_Dialog)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
