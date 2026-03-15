package sch.id.smkdukep.cbt

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    
    // ⚙️ URL UJIAN
    private val examUrl = "https://smkdukep.sch.id/cbt/login"
    private val logoutUrl = "https://smkdukep.sch.id/cbt/logout" // URL Logout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
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
                // Kalau halaman sudah selesai dimuat, pastikan lock task aktif
                enableLockTask()
            }
        }
        
        webView.loadUrl(examUrl)
        
        // Aktifkan Lock Task setelah onCreate
        enableLockTask()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (webView.canGoBack()) {
                    // Kalau bisa back di webview, back saja
                    webView.goBack()
                    return true
                } else {
                    // Kalau sudah di halaman utama, tampilkan konfirmasi keluar
                    showExitConfirmation()
                    return true
                }
            }
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> {
                Toast.makeText(this, "⚠️ Tidak bisa keluar saat ujian", Toast.LENGTH_LONG).show()
                enableLockTask()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showExitConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Konfirmasi Keluar")
            .setMessage("Anda yakin ingin keluar dari aplikasi? Ini akan mengakhiri sesi ujian Anda.")
            .setPositiveButton("Ya, Keluar") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun performLogout() {
        // Matikan lock task dulu
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopLockTask()
        }

        // Muat URL logout di WebView
        webView.loadUrl(logoutUrl)
        
        // Beri waktu untuk proses logout
        handler.postDelayed({
            // Tutup aplikasi
            finishAffinity()
        }, 1000) // Delay 1 detik
    }

    private fun enableLockTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startLockTask()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // Bersihkan handler
    }
}
