package dev.hridaya.kubenexus.presentation.common.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

fun Modifier.scaleOnPress(targetScale: Float = 0.96f, onClick: (() -> Unit)? = null): Modifier =
    composed {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale = animateFloatAsState(
            targetValue = if (isPressed) targetScale else 1f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "scale_on_press",
        )

        this
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
    }
