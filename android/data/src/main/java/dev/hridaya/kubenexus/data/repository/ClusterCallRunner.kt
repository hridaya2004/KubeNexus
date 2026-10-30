package dev.hridaya.kubenexus.data.repository

import android.util.Log
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.log.APP_LOG_TAG_PREFIX
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.KubeconfigEncryptor
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Runs native bridge calls against a cluster.
 *
 * Every repository method used to repeat the same preamble and error handling:
 * look the cluster up, decrypt its kubeconfig, hop to the IO dispatcher, catch,
 * sanitize and log. [run] is that sequence, written once.
 */
class ClusterCallRunner @Inject constructor(
    private val clusterDao: ClusterDao,
    private val encryptor: KubeconfigEncryptor,
    private val dispatcherProvider: DispatcherProvider,
) : KubeconfigProvider {

    override suspend fun kubeconfigOf(clusterId: String): String? =
        (resolve(clusterId) as? Result.Success)?.data?.kubeconfig?.takeIf { it.isNotBlank() }


    /**
     * Resolves [clusterId] and runs [call] against it on the IO dispatcher.
     *
     * Native failures ([AppError.Unknown]) and thrown exceptions become
     * [AppError.Network] with a sanitized message, falling back to
     * [failureMessage]; errors [call] reports deliberately, such as validation,
     * pass through unchanged.
     */
    suspend fun <T> run(
        clusterId: String?,
        failureMessage: String,
        call: suspend (cluster: ResolvedCluster) -> Result<T>,
    ): Result<T> {
        val cluster = when (val resolved = resolve(clusterId)) {
            is Result.Success -> resolved.data
            is Result.Error -> return resolved
            is Result.Loading -> return resolved
        }
        return withContext(dispatcherProvider.io) {
            try {
                call(cluster).asClusterFailure(failureMessage)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val message = LogSanitizer.sanitize(t.message)
                Log.e(TAG, LogSanitizer.withStackTrace("$failureMessage: $message", t))
                Result.Error(AppError.Network(message.ifEmpty { failureMessage }))
            }
        }
    }

    /** Looks [clusterId] up and decrypts its kubeconfig. */
    suspend fun resolve(clusterId: String?): Result<ResolvedCluster> {
        if (clusterId == null) return Result.Error(AppError.NotFound("No cluster selected"))
        val cluster = clusterDao.getClusterById(clusterId)
            ?: return Result.Error(AppError.NotFound("Cluster '$clusterId' not found"))
        return Result.Success(
            ResolvedCluster(
                id = clusterId,
                kubeconfig = encryptor.decryptOrEmpty(cluster.rawKubeconfig, clusterId)
            ),
        )
    }

    private fun <T> Result<T>.asClusterFailure(failureMessage: String): Result<T> {
        val error = (this as? Result.Error)?.error as? AppError.Unknown ?: return this
        val message = LogSanitizer.sanitize(error.message)
        Log.e(TAG, "$failureMessage: $message")
        return Result.Error(AppError.Network(message.ifEmpty { failureMessage }))
    }

    private companion object {
        const val TAG = "$APP_LOG_TAG_PREFIX/ClusterCallRunner"
    }
}

/**
 * A cluster ready to be called: its id and decrypted kubeconfig. The kubeconfig
 * is empty when the stored one can no longer be decrypted; the bridge reports
 * that as unreadable credentials.
 */
class ResolvedCluster(val id: String, val kubeconfig: String)
