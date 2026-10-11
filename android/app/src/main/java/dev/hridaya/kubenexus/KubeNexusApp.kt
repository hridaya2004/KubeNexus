package dev.hridaya.kubenexus

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.hridaya.kubenexus.data.startup.DataLayerInitializer
import javax.inject.Inject

@HiltAndroidApp
class KubeNexusApp : Application() {

    @Inject
    lateinit var dataLayerInitializer: DataLayerInitializer

    override fun onCreate() {
        super.onCreate()
        dataLayerInitializer.initialize()
    }
}
