package com.example.gradule.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.gradule.data.ThemeMode

private val GraduleTypography = Typography().let {
    it.copy(
        displayLarge = TextStyle(fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp),
        displayMedium = TextStyle(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
        titleMedium = it.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun GraduleTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (mode == ThemeMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme(
            primary = Color(0xFFFFD9A0),
            onPrimary = Color(0xFF3B2400),
            surface = Color(0xFF1B1B26),
            background = Color(0xFF0B1026),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = GraduleTypography, content = content)
}
