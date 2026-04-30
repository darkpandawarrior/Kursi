package com.kursi.core.network

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory

/**
 * Returns the platform-appropriate Ktor [HttpClientEngineFactory].
 *
 * | Target      | Engine        | Transport          |
 * |-------------|---------------|--------------------|
 * | jvm         | OkHttp        | OkHttp 4.x         |
 * | android     | OkHttp        | OkHttp 4.x         |
 * | iosArm64 /  | Darwin        | NSURLSession       |
 * | iosSimArm64 |               |                    |
 * | wasmJs      | Js            | browser fetch/WS   |
 *
 * Each platform's [actual] is in its own sourceSet so only the correct engine
 * artifact is pulled per target — no fat-jar / classpath pollution.
 */
internal expect fun defaultHttpClientEngine(): HttpClientEngineFactory<HttpClientEngineConfig>
