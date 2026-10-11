package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.domain.model.ActivePortForwardSession
import dev.hridaya.kubenexus.domain.repository.PortForwardSessionRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Every port-forward the app has open or has recently closed, on any cluster. */
class ObservePortForwardSessionsUseCase @Inject constructor(
    private val repository: PortForwardSessionRepository,
) {
    operator fun invoke(): StateFlow<List<ActivePortForwardSession>> = repository.sessions
}
