package dev.hridaya.kubenexus.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.hridaya.kubenexus.data.resource.ResourceCodec
import dev.hridaya.kubenexus.data.resource.codec.DeploymentCodec
import dev.hridaya.kubenexus.data.resource.codec.PodCodec
import dev.hridaya.kubenexus.data.resource.codec.ServiceCodec

/**
 * Registers the resource kinds the data layer can list, cache, describe, create
 * and delete. Supporting a new kind on the data side is one codec plus one
 * provider here.
 */
@Module
@InstallIn(SingletonComponent::class)
object ResourceCodecModule {

    @Provides
    @IntoSet
    fun providePodCodec(): ResourceCodec<*, *> = PodCodec

    @Provides
    @IntoSet
    fun provideDeploymentCodec(): ResourceCodec<*, *> = DeploymentCodec

    @Provides
    @IntoSet
    fun provideServiceCodec(): ResourceCodec<*, *> = ServiceCodec
}
