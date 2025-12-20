package com.omnisyncra.core.platform

import com.omnisyncra.core.domain.DeviceType
import com.omnisyncra.core.domain.DeviceCapabilities
import com.omnisyncra.core.domain.ComputePower
import com.omnisyncra.core.domain.ScreenSize
import android.os.Build
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import android.app.ActivityManager
import androidx.compose.ui.platform.LocalContext

class AndroidPlatform(private val context: Context) : Platform {
    override val name: String = "Android ${Build.VERSION.RELEASE}"
    override val deviceType: DeviceType = if (isTablet()) DeviceType.ANDROID_TABLET else DeviceType.ANDROID_PHONE
    
    override val capabilities: DeviceCapabilities = DeviceCapabilities(
        computePower = detectComputePower(),
        screenSize = detectScreenSize(),
        hasBluetoothLE = supportsBluetoothLE(),
        hasWiFi = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI),
        canOffloadCompute = false, // Android typically receives compute tasks
        maxConcurrentTasks = getMaxConcurrentTasks(),
        supportedProtocols = listOf("ble", "wifi", "websocket")
    )
    
    override fun getDeviceName(): String = Build.MODEL
    
    override fun getNetworkInterfaces(): List<NetworkInterface> {
        val interfaces = mutableListOf<NetworkInterface>()
        
        // WiFi interface
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiManager.isWifiEnabled) {
            val wifiInfo = wifiManager.connectionInfo
            val ipAddress = intToIp(wifiInfo.ipAddress)
            interfaces.add(
                NetworkInterface(
                    name = "wlan0",
                    ipAddress = ipAddress,
                    isActive = true,
                    type = NetworkType.WIFI
                )
            )
        }
        
        return interfaces
    }
    
    override fun supportsBluetoothLE(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }
    
    override fun supportsWebBluetooth(): Boolean = false
    
    override fun getScreenDimensions(): Pair<Int, Int> {
        val displayMetrics = context.resources.displayMetrics
        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
    
    override fun getAvailableMemory(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem
    }
    
    override fun getCpuCores(): Int = Runtime.getRuntime().availableProcessors()
    
    private fun isTablet(): Boolean {
        val displayMetrics = context.resources.displayMetrics
        val widthDp = displayMetrics.widthPixels / displayMetrics.density
        val heightDp = displayMetrics.heightPixels / displayMetrics.density
        val screenSw = minOf(widthDp, heightDp)
        return screenSw >= 600
    }
    
    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}

actual fun getPlatform(): Platform {
    // This will be injected via DI in real usage
    throw IllegalStateException("Platform should be injected via DI")
}

actual fun detectComputePower(): ComputePower {
    val cores = Runtime.getRuntime().availableProcessors()
    return when {
        cores >= 8 -> ComputePower.HIGH
        cores >= 4 -> ComputePower.MEDIUM
        else -> ComputePower.LOW
    }
}

actual fun detectScreenSize(): ScreenSize {
    // This would need context, will be handled in the actual platform instance
    return ScreenSize.MEDIUM
}

actual fun getMaxConcurrentTasks(): Int {
    val cores = Runtime.getRuntime().availableProcessors()
    return maxOf(2, cores / 2) // Conservative for mobile
}