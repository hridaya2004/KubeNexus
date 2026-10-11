package dev.hridaya.kubenexus.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.hridaya.kubenexus.data.source.local.KubeNexusDatabase
import dev.hridaya.kubenexus.data.source.local.dao.APIResourceDao
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.data.source.local.dao.ExplainedResourceDao
import dev.hridaya.kubenexus.data.source.local.dao.NamespaceDao
import dev.hridaya.kubenexus.data.source.local.dao.OpenApiSchemaDao
import dev.hridaya.kubenexus.data.source.local.dao.ResourceCacheDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): KubeNexusDatabase = KubeNexusDatabase.getInstance(context)

    @Provides
    fun provideClusterDao(
        database: KubeNexusDatabase
    ): ClusterDao = database.clusterDao()

    @Provides
    fun provideNamespaceDao(
        database: KubeNexusDatabase
    ): NamespaceDao = database.namespaceDao()

    @Provides
    fun provideApiResourceDao(
        database: KubeNexusDatabase
    ): APIResourceDao = database.apiResourceDao()

    @Provides
    fun provideExplainedResourceDao(
        database: KubeNexusDatabase
    ): ExplainedResourceDao = database.explainedResourceDao()

    @Provides
    fun provideOpenApiSchemaDao(
        database: KubeNexusDatabase
    ): OpenApiSchemaDao = database.openApiSchemaDao()

    @Provides
    fun provideResourceCacheDao(
        database: KubeNexusDatabase
    ): ResourceCacheDao = database.resourceCacheDao()
}
