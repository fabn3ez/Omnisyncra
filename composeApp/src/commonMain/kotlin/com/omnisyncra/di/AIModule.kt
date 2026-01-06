package com.omnisyncra.di

import com.omnisyncra.core.ai.*
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val aiModule = module {
    // HTTP Client for AI services
    single<HttpClient> {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
    
    // AI System Components
    single<DataSanitizer> { DataSanitizer() }
    single<ContextAnalyzer> { ContextAnalyzer() }
    
    // Gemini API Client
    single<GeminiAPIClient> { 
        val apiKey = getProperty("gemini.api.key", "")
        GeminiAPIClient(apiKey, get())
    }
    
    // Main AI System
    single<AISystem> { 
        val apiKey = getProperty("gemini.api.key", "")
        OmnisyncraAISystem(apiKey, get())
    }
}

// Helper function to get properties (would be implemented platform-specifically)
expect fun getProperty(key: String, defaultValue: String): String