package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.domain.model.ThemeMode
import dev.hridaya.kubenexus.domain.repository.ThemePreferencesRepository
import javax.inject.Inject

class SetThemeModeUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
) {
    suspend operator fun invoke(mode: ThemeMode) = repository.setThemeMode(mode)
}
