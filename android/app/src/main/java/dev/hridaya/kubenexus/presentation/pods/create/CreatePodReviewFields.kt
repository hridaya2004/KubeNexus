package dev.hridaya.kubenexus.presentation.pods.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.presentation.common.components.ErrorBanner
import dev.hridaya.kubenexus.presentation.common.components.StepHeader

@Composable
internal fun CreatePodReviewFields(
    uiState: CreatePodUiState,
    onAction: (CreatePodUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepHeader(stepLabel = "Step 2 of 2", description = "Review the manifest before applying")

        uiState.errorMessage?.let { message ->
            ErrorBanner(
                message = message,
                onDismiss = { onAction(CreatePodUiAction.DismissError) },
            )
        }

        Text(
            text = "You can edit the manifest before applying it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = uiState.reviewedYaml,
            onValueChange = { onAction(CreatePodUiAction.ReviewedYamlChanged(it)) },
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            enabled = !uiState.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 340.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = { onAction(CreatePodUiAction.BackToFormClicked) },
                enabled = !uiState.isSubmitting,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Button(
                onClick = { onAction(CreatePodUiAction.ApplySubmitted) },
                enabled = !uiState.isSubmitting && uiState.reviewedYaml.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Applying",
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    Text(
                        text = "Apply",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
