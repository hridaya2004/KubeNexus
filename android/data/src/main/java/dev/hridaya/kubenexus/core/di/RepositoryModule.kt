package dev.hridaya.kubenexus.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.hridaya.kubenexus.core.common.network.ConnectivityNetworkMonitor
import dev.hridaya.kubenexus.core.common.network.NetworkMonitor
import dev.hridaya.kubenexus.core.security.AndroidKeystoreKubeconfigEncryptor
import dev.hridaya.kubenexus.core.security.KubeconfigEncryptor
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridge
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridgeImpl
import dev.hridaya.kubenexus.data.repository.ClusterRepositoryImpl
import dev.hridaya.kubenexus.data.repository.DeploymentRepositoryImpl
import dev.hridaya.kubenexus.data.repository.ExploreRepositoryImpl
import dev.hridaya.kubenexus.data.repository.FavouriteResourcesRepositoryImpl
import dev.hridaya.kubenexus.data.repository.LogcatRepositoryImpl
import dev.hridaya.kubenexus.data.repository.NamespaceRepositoryImpl
import dev.hridaya.kubenexus.data.repository.PodRepositoryImpl
import dev.hridaya.kubenexus.data.repository.PortForwardRepositoryImpl
import dev.hridaya.kubenexus.data.repository.ResourceRepositoryImpl
import dev.hridaya.kubenexus.data.repository.ThemePreferencesRepositoryImpl
import dev.hridaya.kubenexus.data.source.local.DataStoreThemePreferencesDataSource
import dev.hridaya.kubenexus.data.source.local.DefaultLogcatLocalDataSource
import dev.hridaya.kubenexus.data.source.local.LogcatLocalDataSource
import dev.hridaya.kubenexus.data.source.local.ThemePreferencesLocalDataSource
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import dev.hridaya.kubenexus.domain.repository.DeploymentRepository
import dev.hridaya.kubenexus.domain.repository.ExploreRepository
import dev.hridaya.kubenexus.domain.repository.FavouriteResourcesRepository
import dev.hridaya.kubenexus.domain.repository.LogcatRepository
import dev.hridaya.kubenexus.domain.repository.NamespaceRepository
import dev.hridaya.kubenexus.domain.repository.PodRepository
import dev.hridaya.kubenexus.domain.repository.PortForwardRepository
import dev.hridaya.kubenexus.domain.repository.ResourceRepository
import dev.hridaya.kubenexus.domain.repository.ThemePreferencesRepository
import javax.inject.Singleton

/**
 * Ignore unused warnings.
 */
@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindClusterRepository(
        impl: ClusterRepositoryImpl
    ): ClusterRepository

    @Binds
    @Singleton
    abstract fun bindPodRepository(
        impl: PodRepositoryImpl
    ): PodRepository

    @Binds
    @Singleton
    abstract fun bindDeploymentRepository(
        impl: DeploymentRepositoryImpl
    ): DeploymentRepository

    @Binds
    @Singleton
    abstract fun bindResourceRepository(
        impl: ResourceRepositoryImpl
    ): ResourceRepository

    @Binds
    @Singleton
    abstract fun bindNamespaceRepository(
        impl: NamespaceRepositoryImpl
    ): NamespaceRepository

    @Binds
    @Singleton
    abstract fun bindPortForwardRepository(
        impl: PortForwardRepositoryImpl
    ): PortForwardRepository

    @Binds
    @Singleton
    abstract fun bindExploreRepository(
        impl: ExploreRepositoryImpl
    ): ExploreRepository

    @Binds
    @Singleton
    abstract fun bindLogcatRepository(
        impl: LogcatRepositoryImpl
    ): LogcatRepository

    @Binds
    @Singleton
    abstract fun bindThemePreferencesRepository(
        impl: ThemePreferencesRepositoryImpl
    ): ThemePreferencesRepository

    @Binds
    @Singleton
    abstract fun bindFavouriteResourcesRepository(
        impl: FavouriteResourcesRepositoryImpl
    ): FavouriteResourcesRepository

    @Binds
    @Singleton
    abstract fun bindThemePreferencesLocalDataSource(
        impl: DataStoreThemePreferencesDataSource
    ): ThemePreferencesLocalDataSource

    @Binds
    @Singleton
    abstract fun bindLogcatLocalDataSource(
        impl: DefaultLogcatLocalDataSource
    ): LogcatLocalDataSource

    @Binds
    @Singleton
    abstract fun bindNativeBridge(
        impl: KubeNexusNativeBridgeImpl
    ): KubeNexusNativeBridge

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(
        impl: ConnectivityNetworkMonitor
    ): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindKubeconfigEncryptor(
        impl: AndroidKeystoreKubeconfigEncryptor
    ): KubeconfigEncryptor
}
