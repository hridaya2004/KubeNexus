package dev.hridaya.kubenexus.core.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.hridaya.kubenexus.core.common.dispatcher.DefaultDispatcherProvider
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.log.APP_LOG_TAG_PREFIX
import dev.hridaya.kubenexus.core.security.LogSanitizer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatcherProvider: DispatcherProvider): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + dispatcherProvider.io +
                CoroutineExceptionHandler { _, throwable ->
                    Log.e(TAG, LogSanitizer.withStackTrace("Uncaught failure in the application scope", throwable))
                },
        )

    private const val TAG = "$APP_LOG_TAG_PREFIX/ApplicationScope"
}
