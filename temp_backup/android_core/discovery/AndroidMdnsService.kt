package com.omnisyncra.core.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidMdnsService(
    private val context: Context
) : MdnsService {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    
    private val _discoveredServices = MutableStateFlow<List<MdnsServiceInfo>>(emptyList())
    override val discoveredServices: Flow<List<MdnsServiceInfo>> = _discoveredServices.asStateFlow()
    
    private var isDiscovering = false
    private var isRegistered = false
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    
    override suspend fun startDiscovery(serviceType: String) {
        if (isDiscovering) return
        
        isDiscovering = true
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                isDiscovering = false
            }
            
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                // Handle error
            }
            
            override fun onDiscoveryStarted(serviceType: String?) {
                // Discovery started successfully
            }
            
            override fun onDiscoveryStopped(serviceType: String?) {
                isDiscovering = false
            }
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { nsdInfo ->
                    nsdManager.resolveService(nsdInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            // Handle resolve failure
                        }
                        
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                            serviceInfo?.let { resolved ->
                                val mdnsInfo = resolved.toMdnsServiceInfo()
                                val currentServices = _discoveredServices.value.toMutableList()
                                
                                val existingIndex = currentServices.indexOfFirst { 
                                    it.serviceName == mdnsInfo.serviceName 
                                }
                                
                                if (existingIndex >= 0) {
                                    currentServices[existingIndex] = mdnsInfo
                                } else {
                                    currentServices.add(mdnsInfo)
                                }
                                
                                _discoveredServices.value = currentServices
                            }
                        }
                    })
                }
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { lostService ->
                    val currentServices = _discoveredServices.value.toMutableList()
                    currentServices.removeAll { it.serviceName == lostService.serviceName }
                    _discoveredServices.value = currentServices
                }
            }
        }
        
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            isDiscovering = false
        }
    }
    
    override suspend fun stopDiscovery() {
        if (!isDiscovering) return
        
        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                // Handle error
            }
        }
        
        isDiscovering = false
        _discoveredServices.value = emptyList()
    }
    
    override suspend fun registerService(serviceInfo: MdnsServiceInfo) {
        if (isRegistered) return
        
        val nsdServiceInfo = serviceInfo.toNsdServiceInfo()
        
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                isRegistered = false
            }
            
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                // Handle error
            }
            
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                isRegistered = true
            }
            
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                isRegistered = false
            }
        }
        
        try {
            nsdManager.registerService(nsdServiceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            isRegistered = false
        }
    }
    
    override suspend fun unregisterService() {
        if (!isRegistered) return
        
        registrationListener?.let { listener ->
            try {
                nsdManager.unregisterService(listener)
            } catch (e: Exception) {
                // Handle error
            }
        }
        
        isRegistered = false
    }
    
    override fun isDiscovering(): Boolean = isDiscovering
    
    override fun isRegistered(): Boolean = isRegistered
}

private fun NsdServiceInfo.toMdnsServiceInfo(): MdnsServiceInfo {
    val txtRecords = mutableMapOf<String, String>()
    
    // Extract TXT records (Android NSD doesn't directly expose them in older APIs)
    // This would need platform-specific implementation for full TXT record support
    
    return MdnsServiceInfo(
        serviceName = serviceName ?: "Unknown",
        serviceType = serviceType ?: "_omnisyncra._tcp",
        hostName = host?.hostAddress ?: "unknown",
        port = port,
        txtRecords = txtRecords,
        addresses = listOfNotNull(host?.hostAddress)
    )
}

private fun MdnsServiceInfo.toNsdServiceInfo(): NsdServiceInfo {
    return NsdServiceInfo().apply {
        serviceName = this@toNsdServiceInfo.serviceName
        serviceType = this@toNsdServiceInfo.serviceType
        port = this@toNsdServiceInfo.port
        
        // Set TXT records (requires API level 21+)
        try {
            txtRecords.forEach { (key, value) ->
                setAttribute(key, value)
            }
        } catch (e: Exception) {
            // Handle older Android versions
        }
    }
}