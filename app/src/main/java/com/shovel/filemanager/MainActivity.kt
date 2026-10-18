package com.shovel.filemanager

import androidx.annotation.RequiresApi
import android.os.Build
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContentResolver
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shovel.filemanager.ui.ShovelApp
import com.shovel.filemanager.ui.AppSection
import com.shovel.filemanager.ui.MainViewModel
import com.shovel.filemanager.ui.theme.ShovelTheme
import com.shovel.filemanager.ui.components.LocalStorageRoots
import kotlinx.coroutines.delay
import java.io.File

data class IncomingViewRequest(val uri: Uri, val mimeType: String?)

class MainActivity : AppCompatActivity() {
    private val pendingViewRequest = mutableStateOf<IncomingViewRequest?>(null)
    private val pendingStorageRefresh = mutableStateOf(false)
    private var storageReceiversRegistered = false
    private val usbStorageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            pendingStorageRefresh.value = true
        }
    }
    private val mediaStorageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            pendingStorageRefresh.value = true
        }
    }

    /**
     * Android 12+ removes its splash as soon as the app is ready. This keeps it up until the shovel
     * animation has finished playing, then fades it out.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun holdSplashUntilAnimationEnds() {
        splashScreen.setOnExitAnimationListener { splashView ->
            val start = splashView.iconAnimationStart
            val duration = splashView.iconAnimationDuration
            val remainingMillis = if (start != null && duration != null) {
                duration.toMillis() - java.time.Duration.between(start, java.time.Instant.now()).toMillis()
            } else {
                0L
            }
            val fadeOut = Runnable {
                splashView.animate().alpha(0f).setDuration(150L).withEndAction { splashView.remove() }.start()
            }
            if (remainingMillis > 0L) splashView.postDelayed(fadeOut, remainingMillis) else fadeOut.run()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) holdSplashUntilAnimationEnds()
        pendingViewRequest.value = intent.toIncomingViewRequest()
        pendingStorageRefresh.value = intent.action in storageChangeActions
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val appearance = mainViewModel.appearanceSettings.collectAsStateWithLifecycle()
            val section = mainViewModel.section.collectAsStateWithLifecycle()
            val filesHomeVisible = mainViewModel.filesHomeVisible.collectAsStateWithLifecycle()
            val panel = mainViewModel.panel.collectAsStateWithLifecycle()
            val storageRoots = mainViewModel.roots.collectAsStateWithLifecycle()
            LaunchedEffect(pendingStorageRefresh.value) {
                if (pendingStorageRefresh.value) {
                    mainViewModel.refreshStorageRoots()
                    delay(750)
                    mainViewModel.refreshStorageRoots()
                    pendingStorageRefresh.value = false
                }
            }
            LaunchedEffect(
                section.value,
                filesHomeVisible.value,
                storageRoots.value.size,
                panel.value.path,
                panel.value.loading,
                panel.value.entries.size,
            ) {
                val homeReady = section.value == AppSection.FILES &&
                    filesHomeVisible.value &&
                    storageRoots.value.isNotEmpty()
                val localReady = panel.value.entries.isNotEmpty()
                if (homeReady || localReady) reportFullyDrawn()
            }
            ShovelTheme(settings = appearance.value) {
                CompositionLocalProvider(LocalStorageRoots provides storageRoots.value) {
                    ShovelApp(
                        viewModel = mainViewModel,
                        incomingViewRequest = pendingViewRequest.value,
                        onIncomingViewRequestConsumed = { pendingViewRequest.value = null },
                    )
                }
            }
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingViewRequest.value = intent.toIncomingViewRequest()
        pendingStorageRefresh.value = intent.action in storageChangeActions
    }

    override fun onStart() {
        super.onStart()
        if (storageReceiversRegistered) return
        ContextCompat.registerReceiver(
            this,
            usbStorageReceiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            mediaStorageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
                addDataScheme("file")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        storageReceiversRegistered = true
    }

    override fun onStop() {
        if (storageReceiversRegistered) {
            unregisterReceiver(usbStorageReceiver)
            unregisterReceiver(mediaStorageReceiver)
            storageReceiversRegistered = false
        }
        super.onStop()
    }

    private fun Intent.toIncomingViewRequest(): IncomingViewRequest? {
        if (action !in setOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT)) return null
        val viewUri = data ?: return null
        if (viewUri.scheme !in setOf("content", "file")) return null
        return IncomingViewRequest(viewUri, type)
    }

    companion object {
        private val storageChangeActions = setOf(
            UsbManager.ACTION_USB_DEVICE_ATTACHED,
            UsbManager.ACTION_USB_DEVICE_DETACHED,
            Intent.ACTION_MEDIA_MOUNTED,
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_REMOVED,
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_BAD_REMOVAL,
        )
    }
}
