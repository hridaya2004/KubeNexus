package dev.hridaya.kubenexus.data.startup

import dev.hridaya.kubenexus.core.di.ApplicationScope
import dev.hridaya.kubenexus.data.nativebridge.KubeNexusNativeBridge
import dev.hridaya.kubenexus.data.repository.ClusterRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Work the data layer needs once per process, before any screen talks to a cluster. */
class DataLayerInitializer @Inject constructor(
    private val nativeBridge: KubeNexusNativeBridge,
    private val clusterRepository: ClusterRepositoryImpl,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    fun initialize() {
        nativeBridge.initialize()
        applicationScope.launch { clusterRepository.migrateStoredKubeconfigs() }
    }
}
