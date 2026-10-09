package dev.hridaya.kubenexus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

// ---------------------------------------------------------------------------
// Device font helpers
// ---------------------------------------------------------------------------

private fun createDeviceFontFamily(vararg primaryNames: String): FontFamily {
    val weights = listOf(
        FontWeight.W100,
        FontWeight.W200,
        FontWeight.W300,
        FontWeight.W400,
        FontWeight.W500,
        FontWeight.W600,
        FontWeight.W700,
        FontWeight.W800,
        FontWeight.W900,
    )
    val fontList = mutableListOf<Font>()

    for (name in primaryNames) {
        for (w in weights) {
            fontList.add(Font(DeviceFontFamilyName(name), w, FontStyle.Normal))
            fontList.add(Font(DeviceFontFamilyName(name), w, FontStyle.Italic))
        }
    }

    val standardFallbacks = listOf("google-sans-flex", "google-sans", "roboto", "sans-serif")
    for (fb in standardFallbacks) {
        if (fb !in primaryNames) {
            for (w in weights) {
                fontList.add(Font(DeviceFontFamilyName(fb), w, FontStyle.Normal))
                fontList.add(Font(DeviceFontFamilyName(fb), w, FontStyle.Italic))
            }
        }
    }

    return FontFamily(fontList)
}

// ---------------------------------------------------------------------------
// Device font families — one per M3 type-scale slot
// ---------------------------------------------------------------------------

val TitleLargeEmphasized = createDeviceFontFamily(
    "variable-title-large-emphasized",
    "variable-title-large",
    "google-sans",
    "google-sans-flex",
)

val DisplayLargeFamily = createDeviceFontFamily(
    "variable-display-large",
    "google-sans",
    "roboto",
    "sans-serif",
)
val DisplayMediumFamily = createDeviceFontFamily(
    "variable-display-medium",
    "google-sans",
    "roboto",
    "sans-serif",
)
val DisplaySmallFamily = createDeviceFontFamily(
    "variable-display-small",
    "google-sans",
    "roboto",
    "sans-serif",
)

val HeadlineLargeFamily = createDeviceFontFamily(
    "variable-headline-large",
    "google-sans",
    "roboto",
    "sans-serif",
)
val HeadlineMediumFamily = createDeviceFontFamily(
    "variable-headline-medium",
    "google-sans",
    "roboto",
    "sans-serif",
)
val HeadlineSmallFamily = createDeviceFontFamily(
    "variable-headline-small",
    "google-sans",
    "roboto",
    "sans-serif",
)

val TitleMediumFamily = createDeviceFontFamily(
    "variable-title-medium",
    "google-sans-text",
    "google-sans",
    "roboto",
    "sans-serif",
)
val TitleSmallFamily = createDeviceFontFamily(
    "variable-title-small",
    "google-sans-text",
    "google-sans",
    "roboto",
    "sans-serif",
)

val BodyLargeFamily = createDeviceFontFamily(
    "variable-body-large",
    "google-sans-text",
    "google-sans-flex",
    "google-sans",
)
val BodyMediumFamily = createDeviceFontFamily(
    "variable-body-medium",
    "google-sans-text",
    "google-sans-flex",
    "google-sans",
)
val BodySmallFamily = createDeviceFontFamily(
    "variable-body-small",
    "google-sans-text",
    "google-sans-flex",
    "google-sans",
)

val LabelLargeFamily = createDeviceFontFamily(
    "variable-label-large",
    "google-sans-text",
    "google-sans-flex",
    "google-sans",
)
val LabelMediumFamily = createDeviceFontFamily(
    "variable-label-medium",
    "google-sans-text",
    "google-sans-flex",
    "google-sans",
)
val LabelSmallFamily = createDeviceFontFamily(
    "variable-label-small",
    "google-sans-text",
    "google-sans-flex",
    "google-sans",
)

val AppTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = DisplayLargeFamily),
        displayMedium = base.displayMedium.copy(fontFamily = DisplayMediumFamily),
        displaySmall = base.displaySmall.copy(fontFamily = DisplaySmallFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = HeadlineLargeFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = HeadlineMediumFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = HeadlineSmallFamily),
        titleLarge = base.titleLarge.copy(fontFamily = TitleLargeEmphasized),
        titleMedium = base.titleMedium.copy(fontFamily = TitleMediumFamily),
        titleSmall = base.titleSmall.copy(fontFamily = TitleSmallFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = BodyLargeFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = BodyMediumFamily),
        bodySmall = base.bodySmall.copy(fontFamily = BodySmallFamily),
        labelLarge = base.labelLarge.copy(fontFamily = LabelLargeFamily),
        labelMedium = base.labelMedium.copy(fontFamily = LabelMediumFamily),
        labelSmall = base.labelSmall.copy(fontFamily = LabelSmallFamily),
        displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = DisplayLargeFamily),
        displayMediumEmphasized = base.displayMediumEmphasized.copy(fontFamily = DisplayMediumFamily),
        displaySmallEmphasized = base.displaySmallEmphasized.copy(fontFamily = DisplaySmallFamily),
        headlineLargeEmphasized = base.headlineLargeEmphasized.copy(fontFamily = HeadlineLargeFamily),
        headlineMediumEmphasized = base.headlineMediumEmphasized.copy(fontFamily = HeadlineMediumFamily),
        headlineSmallEmphasized = base.headlineSmallEmphasized.copy(fontFamily = HeadlineSmallFamily),
        titleLargeEmphasized = base.titleLargeEmphasized.copy(fontFamily = TitleLargeEmphasized),
        titleMediumEmphasized = base.titleMediumEmphasized.copy(fontFamily = TitleMediumFamily),
        titleSmallEmphasized = base.titleSmallEmphasized.copy(fontFamily = TitleSmallFamily),
        bodyLargeEmphasized = base.bodyLargeEmphasized.copy(fontFamily = BodyLargeFamily),
        bodyMediumEmphasized = base.bodyMediumEmphasized.copy(fontFamily = BodyMediumFamily),
        bodySmallEmphasized = base.bodySmallEmphasized.copy(fontFamily = BodySmallFamily),
        labelLargeEmphasized = base.labelLargeEmphasized.copy(fontFamily = LabelLargeFamily),
        labelMediumEmphasized = base.labelMediumEmphasized.copy(fontFamily = LabelMediumFamily),
        labelSmallEmphasized = base.labelSmallEmphasized.copy(fontFamily = LabelSmallFamily),
    )
}
