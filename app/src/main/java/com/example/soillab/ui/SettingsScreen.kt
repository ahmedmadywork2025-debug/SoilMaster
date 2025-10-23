package com.example.soillab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.soillab.R
import com.example.soillab.util.LanguageViewModel
import com.example.soillab.util.ThemeMode
import com.example.soillab.util.ThemeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(languageViewModel: LanguageViewModel, themeViewModel: ThemeViewModel) {
    val currentLanguage by languageViewModel.currentLanguage.collectAsState()
    val currentTheme by themeViewModel.currentThemeMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(id = R.string.select_language),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = currentLanguage == "en",
                onClick = { languageViewModel.setLanguage("en") },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text(stringResource(R.string.english))
            }
            SegmentedButton(
                selected = currentLanguage == "ar",
                onClick = { languageViewModel.setLanguage("ar") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text(stringResource(R.string.arabic))
            }
        }

        Divider()

        Text(
            text = stringResource(id = R.string.select_theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = currentTheme == ThemeMode.SYSTEM,
                onClick = { themeViewModel.setThemeMode(ThemeMode.SYSTEM) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) {
                Text(stringResource(R.string.theme_system))
            }
            SegmentedButton(
                selected = currentTheme == ThemeMode.LIGHT,
                onClick = { themeViewModel.setThemeMode(ThemeMode.LIGHT) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) {
                Text(stringResource(R.string.theme_light))
            }
            SegmentedButton(
                selected = currentTheme == ThemeMode.DARK,
                onClick = { themeViewModel.setThemeMode(ThemeMode.DARK) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) {
                Text(stringResource(R.string.theme_dark))
            }
        }
    }
}

