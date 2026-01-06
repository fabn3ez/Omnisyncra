package com.omnisyncra.test

import com.omnisyncra.core.ai.*
import com.omnisyncra.core.security.*
import io.ktor.client.*
import kotlinx.coroutines.runBlocking

/**
 * Simple test to verify AI and Security systems work
 */
fun testSystems() {
    println("🚀 Testing Omnisyncra Systems...")
    
    runBlocking {
        // Test Security System
        println("\n🔒 Testing Security System...")
        val securitySystem = OmnisyncraSecuritySystem("test-device")
        
        val initResult = securitySystem.initialize()
        if (initResult.isSuccess) {
            println("✅ Security system initialized successfully")
            
            // Test encryption
            val key = securitySystem.generateKey().getOrNull()
            if (key != null) {
                val testData = "Hello, Omnisyncra!".encodeToByteArray()
                val encryptResult = securitySystem.encrypt(testData, key)
                
                if (encryptResult.isSuccess) {
                    println("✅ Encryption successful")
                    
                    val decryptResult = securitySystem.decrypt(encryptResult.getOrThrow(), key)
                    if (decryptResult.isSuccess) {
                        val decryptedText = decryptResult.getOrThrow().decodeToString()
                        if (decryptedText == "Hello, Omnisyncra!") {
                            println("✅ Decryption successful: $decryptedText")
                        } else {
                            println("❌ Decryption failed: text mismatch")
                        }
                    } else {
                        println("❌ Decryption failed: ${decryptResult.exceptionOrNull()}")
                    }
                } else {
                    println("❌ Encryption failed: ${encryptResult.exceptionOrNull()}")
                }
            } else {
                println("❌ Key generation failed")
            }
        } else {
            println("❌ Security system initialization failed: ${initResult.exceptionOrNull()}")
        }
        
        // Test AI System (without API key for now)
        println("\n🧠 Testing AI System...")
        val httpClient = HttpClient()
        val aiSystem = OmnisyncraAISystem("test-key", httpClient)
        
        val aiInitResult = aiSystem.initialize()
        if (aiInitResult.isSuccess) {
            println("✅ AI system initialized successfully")
            
            // Test data sanitization
            val testText = "Contact John Doe at john.doe@example.com or call 555-123-4567"
            val sanitizeResult = aiSystem.sanitizeData(testText)
            
            if (sanitizeResult.isSuccess) {
                val sanitized = sanitizeResult.getOrThrow()
                println("✅ Data sanitization successful")
                println("   Original: $testText")
                println("   Sanitized: ${sanitized.sanitizedContent}")
                println("   PII detected: ${sanitized.detectedPII.size} items")
            } else {
                println("❌ Data sanitization failed: ${sanitizeResult.exceptionOrNull()}")
            }
            
            // Test context analysis
            val context = AIContext(
                currentActivity = "Testing AI system",
                recentActivities = listOf("Initialize system", "Run tests"),
                deviceContext = DeviceContext(
                    deviceType = "test-device",
                    capabilities = listOf("compute", "network")
                )
            )
            
            val contextResult = aiSystem.analyzeContext(context)
            if (contextResult.isSuccess) {
                val analysis = contextResult.getOrThrow()
                println("✅ Context analysis successful")
                println("   Topics: ${analysis.relevantTopics}")
                println("   Suggestions: ${analysis.suggestedActions}")
                println("   Confidence: ${analysis.confidenceScore}")
            } else {
                println("❌ Context analysis failed: ${contextResult.exceptionOrNull()}")
            }
        } else {
            println("❌ AI system initialization failed: ${aiInitResult.exceptionOrNull()}")
        }
        
        httpClient.close()
        securitySystem.shutdown()
    }
    
    println("\n🎉 System test completed!")
}