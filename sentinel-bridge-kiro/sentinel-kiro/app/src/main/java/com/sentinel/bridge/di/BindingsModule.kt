package com.sentinel.bridge.di

import com.sentinel.bridge.core.data.repository.FileStorageProvider
import com.sentinel.bridge.core.domain.interfaces.AIProvider
import com.sentinel.bridge.core.domain.interfaces.ActionProvider
import com.sentinel.bridge.core.domain.interfaces.RuleProvider
import com.sentinel.bridge.core.domain.interfaces.StorageProvider
import com.sentinel.bridge.feature.ai.provider.LlamaCppProvider
import com.sentinel.bridge.feature.ai.rules.RulesEngine
import com.sentinel.bridge.feature.pipeline.MacroDroidActionProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding plugin interfaces to their production implementations.
 *
 * These bindings follow the microkernel architecture: the kernel (PipelineOrchestrator
 * + CommandBus) depends only on interfaces. Concrete implementations are resolved here
 * at compile time via Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    /**
     * Binds [AIProvider] to [LlamaCppProvider] for on-device inference via llama.cpp JNI.
     */
    @Binds
    @Singleton
    abstract fun bindAIProvider(impl: LlamaCppProvider): AIProvider

    /**
     * Binds [StorageProvider] to [FileStorageProvider] for transcript persistence
     * at `getExternalFilesDir("transcripts")`.
     */
    @Binds
    @Singleton
    abstract fun bindStorageProvider(impl: FileStorageProvider): StorageProvider

    /**
     * Binds [RuleProvider] to [RulesEngine] for pre-AI and post-AI rule evaluation
     * from `assets/rules/default_rules.json`.
     */
    @Binds
    @Singleton
    abstract fun bindRuleProvider(impl: RulesEngine): RuleProvider

    /**
     * Binds [ActionProvider] to [MacroDroidActionProvider] for broadcasting
     * PIPELINE_COMPLETE and PIPELINE_FAILED intents.
     */
    @Binds
    @Singleton
    abstract fun bindActionProvider(impl: MacroDroidActionProvider): ActionProvider
}
