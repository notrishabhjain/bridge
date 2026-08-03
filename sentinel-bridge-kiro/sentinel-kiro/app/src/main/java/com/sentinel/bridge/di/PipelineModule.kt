package com.sentinel.bridge.di

import com.sentinel.bridge.feature.pipeline.CommandHandler
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import com.sentinel.bridge.feature.pipeline.handlers.BuildPromptHandler
import com.sentinel.bridge.feature.pipeline.handlers.ClickShowTextHandler
import com.sentinel.bridge.feature.pipeline.handlers.DispatchActionHandler
import com.sentinel.bridge.feature.pipeline.handlers.ExtractTranscriptHandler
import com.sentinel.bridge.feature.pipeline.handlers.OpenRecorderHandler
import com.sentinel.bridge.feature.pipeline.handlers.OpenRecordingHandler
import com.sentinel.bridge.feature.pipeline.handlers.ParseResponseHandler
import com.sentinel.bridge.feature.pipeline.handlers.ReturnIntentHandler
import com.sentinel.bridge.feature.pipeline.handlers.RunInferenceHandler
import com.sentinel.bridge.feature.pipeline.handlers.RunPreprocessorHandler
import com.sentinel.bridge.feature.pipeline.handlers.RunRulesPostAIHandler
import com.sentinel.bridge.feature.pipeline.handlers.RunRulesPreAIHandler
import com.sentinel.bridge.feature.pipeline.handlers.SelectLanguageHandler
import com.sentinel.bridge.feature.pipeline.handlers.StoreResultHandler
import com.sentinel.bridge.feature.pipeline.handlers.ValidateJsonHandler
import com.sentinel.bridge.feature.pipeline.handlers.WaitForTranscriptionHandler
import com.sentinel.bridge.native_.NativeBridge
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import javax.inject.Singleton

/**
 * Hilt module providing pipeline infrastructure: [NativeBridge] singleton and
 * all 16 [CommandHandler] bindings via Hilt multibinding.
 *
 * Each handler is bound into the `Map<Class<out PipelineCommand>, CommandHandler<PipelineCommand>>`
 * that the [CommandBus] uses for dispatch resolution. The `@ClassKey` annotation maps
 * each [PipelineCommand] subclass to its dedicated handler.
 */
@Module
@InstallIn(SingletonComponent::class)
object PipelineModule {

    /**
     * Provides the singleton [NativeBridge] JNI wrapper.
     *
     * Only [LlamaCppProvider] should consume this — no other component accesses
     * the native layer directly.
     */
    @Provides
    @Singleton
    fun provideNativeBridge(): NativeBridge {
        return NativeBridge()
    }

    /**
     * Binds [OpenRecorderHandler] to handle [PipelineCommand.OpenRecorder] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.OpenRecorder::class)
    @Suppress("UNCHECKED_CAST")
    fun provideOpenRecorderHandler(
        handler: OpenRecorderHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [OpenRecordingHandler] to handle [PipelineCommand.OpenRecording] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.OpenRecording::class)
    @Suppress("UNCHECKED_CAST")
    fun provideOpenRecordingHandler(
        handler: OpenRecordingHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [ClickShowTextHandler] to handle [PipelineCommand.ClickShowText] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.ClickShowText::class)
    @Suppress("UNCHECKED_CAST")
    fun provideClickShowTextHandler(
        handler: ClickShowTextHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [SelectLanguageHandler] to handle [PipelineCommand.SelectLanguage] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.SelectLanguage::class)
    @Suppress("UNCHECKED_CAST")
    fun provideSelectLanguageHandler(
        handler: SelectLanguageHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [WaitForTranscriptionHandler] to handle [PipelineCommand.WaitForTranscription] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.WaitForTranscription::class)
    @Suppress("UNCHECKED_CAST")
    fun provideWaitForTranscriptionHandler(
        handler: WaitForTranscriptionHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [ExtractTranscriptHandler] to handle [PipelineCommand.ExtractTranscript] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.ExtractTranscript::class)
    @Suppress("UNCHECKED_CAST")
    fun provideExtractTranscriptHandler(
        handler: ExtractTranscriptHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [RunPreprocessorHandler] to handle [PipelineCommand.RunPreprocessor] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.RunPreprocessor::class)
    @Suppress("UNCHECKED_CAST")
    fun provideRunPreprocessorHandler(
        handler: RunPreprocessorHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [RunRulesPreAIHandler] to handle [PipelineCommand.RunRulesPreAI] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.RunRulesPreAI::class)
    @Suppress("UNCHECKED_CAST")
    fun provideRunRulesPreAIHandler(
        handler: RunRulesPreAIHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [BuildPromptHandler] to handle [PipelineCommand.BuildPrompt] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.BuildPrompt::class)
    @Suppress("UNCHECKED_CAST")
    fun provideBuildPromptHandler(
        handler: BuildPromptHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [RunInferenceHandler] to handle [PipelineCommand.RunInference] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.RunInference::class)
    @Suppress("UNCHECKED_CAST")
    fun provideRunInferenceHandler(
        handler: RunInferenceHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [ParseResponseHandler] to handle [PipelineCommand.ParseResponse] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.ParseResponse::class)
    @Suppress("UNCHECKED_CAST")
    fun provideParseResponseHandler(
        handler: ParseResponseHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [ValidateJsonHandler] to handle [PipelineCommand.ValidateJson] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.ValidateJson::class)
    @Suppress("UNCHECKED_CAST")
    fun provideValidateJsonHandler(
        handler: ValidateJsonHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [RunRulesPostAIHandler] to handle [PipelineCommand.RunRulesPostAI] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.RunRulesPostAI::class)
    @Suppress("UNCHECKED_CAST")
    fun provideRunRulesPostAIHandler(
        handler: RunRulesPostAIHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [StoreResultHandler] to handle [PipelineCommand.StoreResult] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.StoreResult::class)
    @Suppress("UNCHECKED_CAST")
    fun provideStoreResultHandler(
        handler: StoreResultHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [DispatchActionHandler] to handle [PipelineCommand.DispatchAction] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.DispatchAction::class)
    @Suppress("UNCHECKED_CAST")
    fun provideDispatchActionHandler(
        handler: DispatchActionHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }

    /**
     * Binds [ReturnIntentHandler] to handle [PipelineCommand.ReturnIntent] commands.
     */
    @Provides
    @IntoMap
    @ClassKey(PipelineCommand.ReturnIntent::class)
    @Suppress("UNCHECKED_CAST")
    fun provideReturnIntentHandler(
        handler: ReturnIntentHandler
    ): CommandHandler<PipelineCommand> {
        return handler as CommandHandler<PipelineCommand>
    }
}
