package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.domain.repository.ThemePreferencesRepository
import javax.inject.Inject

class SetAmoledDarkUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setAmoledDark(enabled)
}
