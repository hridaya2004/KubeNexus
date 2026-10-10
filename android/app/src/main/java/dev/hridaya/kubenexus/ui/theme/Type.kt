package dev.hridaya.kubenexus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Fonts tried in order when the device has no font of its own for a type-scale slot: Google Sans,
 * then the system's default font, then Roboto.
 */
private val fallbackFonts = listOf("google-sans", "sans-serif", "roboto")

private val fontWeights = listOf(
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

/** The device's fonts named first, then [fallbackFonts]. */
private fun deviceFontFamily(vararg names: String): FontFamily = FontFamily(
    (names.toList() + fallbackFonts).flatMap { name ->
        fontWeights.flatMap { weight ->
            listOf(FontStyle.Normal, FontStyle.Italic).map { style ->
                Font(DeviceFontFamilyName(name), weight, style)
            }
        }
    },
)

/** The device's font for a type-scale slot, such as `title-large`. */
private fun TextStyle.withDeviceFont(slot: String): TextStyle =
    copy(fontFamily = deviceFontFamily("variable-$slot"))

/** The device's emphasized font for a type-scale slot, or its regular one if it has none. */
private fun TextStyle.withEmphasizedDeviceFont(slot: String): TextStyle =
    copy(fontFamily = deviceFontFamily("variable-$slot-emphasized", "variable-$slot"))

/**
 * Material's type scale set in the device's own fonts. Display small, headline medium and title
 * large are their emphasized versions, so the app bar titles set in them are emphasized.
 */
val AppTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.withDeviceFont("display-large"),
        displayMedium = base.displayMedium.withDeviceFont("display-medium"),
        displaySmall = base.displaySmallEmphasized.withEmphasizedDeviceFont("display-small"),
        headlineLarge = base.headlineLarge.withDeviceFont("headline-large"),
        headlineMedium = base.headlineMediumEmphasized.withEmphasizedDeviceFont("headline-medium"),
        headlineSmall = base.headlineSmall.withDeviceFont("headline-small"),
        titleLarge = base.titleLargeEmphasized.withEmphasizedDeviceFont("title-large"),
        titleMedium = base.titleMedium.withDeviceFont("title-medium"),
        titleSmall = base.titleSmall.withDeviceFont("title-small"),
        bodyLarge = base.bodyLarge.withDeviceFont("body-large"),
        bodyMedium = base.bodyMedium.withDeviceFont("body-medium"),
        bodySmall = base.bodySmall.withDeviceFont("body-small"),
        labelLarge = base.labelLarge.withDeviceFont("label-large"),
        labelMedium = base.labelMedium.withDeviceFont("label-medium"),
        labelSmall = base.labelSmall.withDeviceFont("label-small"),
        displayLargeEmphasized = base.displayLargeEmphasized.withEmphasizedDeviceFont("display-large"),
        displayMediumEmphasized = base.displayMediumEmphasized.withEmphasizedDeviceFont("display-medium"),
        displaySmallEmphasized = base.displaySmallEmphasized.withEmphasizedDeviceFont("display-small"),
        headlineLargeEmphasized = base.headlineLargeEmphasized.withEmphasizedDeviceFont("headline-large"),
        headlineMediumEmphasized = base.headlineMediumEmphasized.withEmphasizedDeviceFont("headline-medium"),
        headlineSmallEmphasized = base.headlineSmallEmphasized.withEmphasizedDeviceFont("headline-small"),
        titleLargeEmphasized = base.titleLargeEmphasized.withEmphasizedDeviceFont("title-large"),
        titleMediumEmphasized = base.titleMediumEmphasized.withEmphasizedDeviceFont("title-medium"),
        titleSmallEmphasized = base.titleSmallEmphasized.withEmphasizedDeviceFont("title-small"),
        bodyLargeEmphasized = base.bodyLargeEmphasized.withEmphasizedDeviceFont("body-large"),
        bodyMediumEmphasized = base.bodyMediumEmphasized.withEmphasizedDeviceFont("body-medium"),
        bodySmallEmphasized = base.bodySmallEmphasized.withEmphasizedDeviceFont("body-small"),
        labelLargeEmphasized = base.labelLargeEmphasized.withEmphasizedDeviceFont("label-large"),
        labelMediumEmphasized = base.labelMediumEmphasized.withEmphasizedDeviceFont("label-medium"),
        labelSmallEmphasized = base.labelSmallEmphasized.withEmphasizedDeviceFont("label-small"),
    )
}
