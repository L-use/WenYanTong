package com.wenyantong.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary=Color(0xFFAE5B40),onPrimary=Color.White,primaryContainer=Color(0xFFF4E4DA),onPrimaryContainer=Color(0xFF743C29),
    background=Color(0xFFFAF9F5),onBackground=Color(0xFF25211E),surface=Color(0xFFFFFFFF),onSurface=Color(0xFF25211E),
    surfaceVariant=Color(0xFFF3EFE7),onSurfaceVariant=Color(0xFF746D66),outline=Color(0xFF9A8F85),outlineVariant=Color(0xFFE6DFD8),
    secondary=Color(0xFF746D66),secondaryContainer=Color(0xFFF0EBE2),onSecondaryContainer=Color(0xFF25211E)
)
private val Dark = darkColorScheme(
    primary=Color(0xFFE5A58A),onPrimary=Color(0xFF39241C),primaryContainer=Color(0xFF54392D),onPrimaryContainer=Color(0xFFF5D9CA),
    background=Color(0xFF201E1B),onBackground=Color(0xFFF3EDE5),surface=Color(0xFF292622),onSurface=Color(0xFFF3EDE5),
    surfaceVariant=Color(0xFF36312B),onSurfaceVariant=Color(0xFFC5BBB0),outline=Color(0xFFA79A8E),outlineVariant=Color(0xFF494039),
    secondary=Color(0xFFCAB9A8),onSecondary=Color(0xFF312921),secondaryContainer=Color(0xFF46392E),onSecondaryContainer=Color(0xFFF0DED0)
)

@Composable fun WenYanTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    MaterialTheme(colorScheme=if(dark) Dark else Light,typography=Typography(),content=content)
}
