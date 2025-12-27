package com.omnisyncra.di

import com.omnisyncra.core.ai.*
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Dependency injection module for AI system components
 */
val aiModule = module {
    
    // JSON configuration for API communication
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }
    
    // HTTP client for Gemini API
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(get<Json>())
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }
    
    // Gemini API configuration
    single {
        GeminiAPIConfig(
            apiKey = getGeminiApiKey(),
            endpoint = "https://generativelanguage.googleapis.com/v1beta",
            model = "gemini-pro",
            maxTokens = 8192,
            temperature = 0.1f,
            rateLimitPerMinute = 60
        )
    }
    
    // Core AI components
    singleOf(::OmnisyncraDataSanitizer) bind DataSanitizer::class
    singleOf(::OmnisyncraContextAnalyzer) bind ContextAnalyzer::class
    
    // Gemini API client
    single<GeminiAPIClient> {
        OmnisyncraGeminiClient(
            config = get(),
            httpClient = get(),
            json = get()
        )
    }
    
    // Main AI system
    single<AISystem> {
        OmnisyncraAISystem(
            dataSanitizer = get(),
            contextAnalyzer = get(),
            geminiClient = get(),
            securitySystem = get()
        )
    }
}

/**
 * Get Gemini API key from environment or configuration
 * Priority: Environment Variable > local.properties > fallback
 */
private fun getGeminiApiKey(): String {
    // First try environment variable
    System.getenv("GEMINI_API_KEY")?.let { return it }
    
    // Then try system property (can be set from local.properties via Gradle)
    System.getProperty("gemini.api.key")?.let { return it }
    
    // Fallback - replace with your actual key for testing
    return "YOUR_ACTUAL_GEMINI_API_KEY_HERE"
}