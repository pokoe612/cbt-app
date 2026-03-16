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
import android.util.Log
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val alertHandler = Handler(Looper.getMainLooper())
    
    // URL UJIAN
    private val examUrl = "https://smkdukep.sch.id/cbt/login"
    private val logoutUrl = "https://smkdukep.sch.id/cbt/logout"
    
    private var isExiting = false
    private var isAlertShowing = false
    private var isLockTaskExiting = false
    private var alertTimerRunnable: Runnable? = null
    private var lockTaskExitRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
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
                if (!isExiting && !isLockTaskExiting) {
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
                // LANGSUNG tampilkan alert, tanpa navigasi halaman
                showExitConfirmation()
                return true
            }
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> {
                Toast.makeText(this, "⚠️ Mode Ujian Aktif", Toast.LENGTH_SHORT).show()
                // Pastikan lock task tetap aktif
                mainHandler.postDelayed({
                    enableLockTask()
                }, 100)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        if (!hasFocus && !isExiting && !isLockTaskExiting) {
            // Deteksi user mencoba keluar dari lock task
            detectLockTaskExit()
        }
        
        if (hasFocus && isLockTaskExiting) {
            // Jika kembali ke aplikasi sebelum 3 detik, batalkan logout
            cancelLockTaskExit()
        }
    }

    /**
     * Deteksi user mencoba keluar dari lock task
     * Jika iya, mulai timer 3 detik untuk logout otomatis
     */
    private fun detectLockTaskExit() {
        isLockTaskExiting = true
        
        // Batalkan timer sebelumnya jika ada
        lockTaskExitRunnable?.let { mainHandler.removeCallbacks(it) }
        
        // Tampilkan peringatan
        Toast.makeText(this, "⚠️ PERINGATAN: Akan logout otomatis dalam 3 detik jika tetap keluar!", Toast.LENGTH_LONG).show()
        
        // Timer 3 detik untuk logout
        lockTaskExitRunnable = Runnable {
            if (isLockTaskExiting) {
                // User benar-benar keluar, lakukan logout
                performForceLogout("Keluar paksa dari lock task")
            }
        }
        
        mainHandler.postDelayed(lockTaskExitRunnable!!, 3000)
    }

    /**
     * Batalkan proses logout jika user kembali ke aplikasi
     */
    private fun cancelLockTaskExit() {
        if (isLockTaskExiting) {
            isLockTaskExiting = false
            lockTaskExitRunnable?.let { mainHandler.removeCallbacks(it) }
            Toast.makeText(this, "✅ Kembali ke ujian, logout dibatalkan", Toast.LENGTH_SHORT).show()
            
            // Aktifkan lock task kembali
            if (!isExiting) {
                enableLockTask()
            }
        }
    }

    /**
     * Force logout ketika user memaksa keluar dari lock task
     */
    private fun performForceLogout(reason: String) {
        if (isExiting) return
        
        isExiting = true
        isLockTaskExiting = false
        isAlertShowing = false
        
        Log.d("LockTask", "Force logout karena: $reason")
        
        Toast.makeText(this, "⚠️ Logout otomatis karena mencoba keluar dari aplikasi!", Toast.LENGTH_LONG).show()
        
        // Matikan lock task
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopLockTask()
        }
        
        // Load URL logout
        webView.loadUrl(logoutUrl)
        
        // Tunggu sebentar lalu tutup aplikasi
        mainHandler.postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webView.clearHistory()
                webView.clearCache(true)
                CookieManager.getInstance().removeAllCookies(null)
            }
            finishAffinity()
        }, 1500)
    }

    /**
     * Menampilkan konfirmasi keluar selama 1 DETIK
     * Tombol BACK langsung memanggil fungsi ini
     */
    private fun showExitConfirmation() {
        if (isAlertShowing || isLockTaskExiting) return
        
        isAlertShowing = true
        
        // 🔒 LOCK TASK TETAP AKTIF - TIDAK DIMATIKAN
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        //     stopLockTask()  // <-- INI DIHAPUS
        // }
        
        alertTimerRunnable?.let { alertHandler.removeCallbacks(it) }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("⚠️ Konfirmasi Keluar")
            .setMessage("Anda yakin ingin keluar dari aplikasi? Ini akan mengakhiri sesi ujian Anda.\n\n⏱️ Alert akan otomatis tertutup dalam 1 DETIK jika tidak ada pilihan.")
            .setPositiveButton("Ya, Keluar") { _, _ ->
                alertTimerRunnable?.let { alertHandler.removeCallbacks(it) }
                isAlertShowing = false
                performLogout()
            }
            .setNegativeButton("Batal") { _, _ ->
                alertTimerRunnable?.let { alertHandler.removeCallbacks(it) }
                isAlertShowing = false
                Toast.makeText(this, "✅ Ujian dilanjutkan", Toast.LENGTH_SHORT).show()
                // Lock task sudah aktif, tidak perlu reactivate
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // Timer 1 DETIK untuk auto close
        alertTimerRunnable = Runnable {
            if (dialog.isShowing) {
                dialog.dismiss()
                isAlertShowing = false
                Toast.makeText(this, "⏱️ Waktu habis, ujian dilanjutkan", Toast.LENGTH_SHORT).show()
                // Lock task sudah aktif, tidak perlu reactivate
            }
        }
        
        alertHandler.postDelayed(alertTimerRunnable!!, 1000)
    }

    /**
     * Proses logout normal (dari alert)
     */
    private fun performLogout() {
        isExiting = true
        isAlertShowing = false
        isLockTaskExiting = false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopLockTask()
        }
        
        webView.loadUrl(logoutUrl)
        
        mainHandler.postDelayed({
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !isExiting && !isLockTaskExiting) {
            try {
                startLockTask()
                hideSystemUI()
                Log.d("LockTask", "Lock task aktif")
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
        mainHandler.removeCallbacksAndMessages(null)
        alertHandler.removeCallbacksAndMessages(null)
    }
}
