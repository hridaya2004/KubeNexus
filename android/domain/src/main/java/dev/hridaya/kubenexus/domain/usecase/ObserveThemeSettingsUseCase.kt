package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.domain.model.ThemeSettings
import dev.hridaya.kubenexus.domain.repository.ThemePreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveThemeSettingsUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
) {
    operator fun invoke(): Flow<ThemeSettings> =
        combine(repository.getThemeModeStream(), repository.getAmoledDarkStream(), ::ThemeSettings)
}
