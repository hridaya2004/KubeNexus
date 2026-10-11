package dev.hridaya.kubenexus.presentation.services.detail

import dev.hridaya.kubenexus.domain.model.ServiceDetails

data class ServiceDetailUiState(
    val serviceName: String,
    val namespace: String,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val lastRefreshedAt: Long? = null,
    val service: ServiceDetails? = null,
    val errorMessage: String? = null,
    val staleNotice: String? = null,
    val showPortForwardDialog: Boolean = false,
)

sealed interface ServiceDetailUiAction {
    data object Refresh : ServiceDetailUiAction
    data class ShowPortForwardDialog(val show: Boolean) : ServiceDetailUiAction
}
