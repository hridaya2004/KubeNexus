package dev.hridaya.kubenexus.core.di

import javax.inject.Qualifier

/** The [kotlinx.coroutines.CoroutineScope] that lives as long as the process. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
