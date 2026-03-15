package sch.id.smkdukep.cbt

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    
    // ⚙️ GANTI URL UJIAN ANDA DI SINI
    private val examUrl = "https://smkdukep.sch.id"

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
        
        webView.webViewClient = WebViewClient()
        webView.loadUrl(examUrl)
        
        // Aktifkan Lock Task setelah onCreate
        enableLockTask()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> {
                Toast.makeText(this, "⚠️ Tidak bisa keluar saat ujian", Toast.LENGTH_SHORT).show()
                enableLockTask()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun enableLockTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startLockTask()
        }
    }
}
