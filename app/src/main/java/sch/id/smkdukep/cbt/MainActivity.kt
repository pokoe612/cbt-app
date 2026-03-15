package sch.id.smkdukep.cbt

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    
    // URL UJIAN
    private val examUrl = "https://smkdukep.sch.id/cbt/login"
    private val logoutUrl = "https://smkdukep.sch.id/cbt/logout"
    
    private var isExiting = false
    private var isAlertShowing = false // Flag untuk alert
    private var countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Inisialisasi DevicePolicyManager
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
        
        // Setup WebView
        webView = findViewById(R.id.webView)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!isAlertShowing) { // Hanya lock jika tidak ada alert
                    enableLockTask()
                }
            }
        }
        
        webView.loadUrl(examUrl)
        enableLockTask()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return true
                } else {
                    showExitConfirmation()
                    return true
                }
            }
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> {
                if (!isAlertShowing) { // Hanya tampilkan toast jika tidak ada alert
                    Toast.makeText(this, "⚠️ Tidak bisa keluar dari mode ujian", Toast.LENGTH_LONG).show()
                    handler.postDelayed({
                        enableLockTask()
                    }, 100)
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && !isExiting && !isAlertShowing) { // Abaikan jika alert sedang muncul
            handler.postDelayed({
                if (!isExiting && !isAlertShowing) {
                    enableLockTask()
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                }
            }, 200)
        }
    }

    /**
     * Menampilkan konfirmasi keluar selama 5 detik
     * - Klik Ya → Logout
     * - Klik Batal → Batal
     * - Diam 5 detik → Otomatis Batal
     */
    private fun showExitConfirmation() {
        // Set flag alert sedang muncul
        isAlertShowing = true
        
        // Nonaktifkan lock task sementara agar alert bisa jalan normal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopLockTask()
        }
        
        // Batalkan timer sebelumnya jika ada
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        
        // Buat dialog
        val dialog = AlertDialog.Builder(this)
            .setTitle("⚠️ Konfirmasi Keluar")
            .setMessage("Anda yakin ingin keluar dari aplikasi? Ini akan mengakhiri sesi ujian Anda.\n\n⏱️ Alert akan otomatis tertutup dalam 5 detik jika tidak ada pilihan.")
            .setPositiveButton("Ya, Keluar") { _, _ ->
                // User memilih Ya
                countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
                isAlertShowing = false
                performLogout()
            }
            .setNegativeButton("Batal") { _, _ ->
                // User memilih Batal
                countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
                isAlertShowing = false
                Toast.makeText(this, "✅ Ujian dilanjutkan", Toast.LENGTH_SHORT).show()
                // Aktifkan lock task kembali
                enableLockTask()
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // Timer 5 detik untuk auto close (dianggap batal)
        countdownRunnable = Runnable {
            if (dialog.isShowing) {
                dialog.dismiss()
                isAlertShowing = false
                Toast.makeText(this, "⏱️ Tidak ada pilihan, ujian dilanjutkan", Toast.LENGTH_SHORT).show()
                // Aktifkan lock task kembali
                enableLockTask()
            }
        }
        
        // Jalankan timer 5 detik
        countdownHandler.postDelayed(countdownRunnable!!, 5000)
    }

    /**
     * Proses logout dan keluar aplikasi
     */
    private fun performLogout() {
        isExiting = true
        
        // Matikan lock task
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopLockTask()
        }
        
        // Load URL logout
        webView.loadUrl(logoutUrl)
        
        // Tunggu sebentar lalu tutup aplikasi
        handler.postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webView.clearHistory()
                webView.clearCache(true)
                CookieManager.getInstance().removeAllCookies(null)
            }
            finishAffinity()
        }, 1500)
    }

    /**
     * Mengaktifkan lock task dan menyembunyikan UI
     */
    private fun enableLockTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !isExiting && !isAlertShowing) {
            try {
                startLockTask()
                hideSystemUI()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Menyembunyikan navigation bar dan status bar
     */
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        countdownHandler.removeCallbacksAndMessages(null)
    }
}
