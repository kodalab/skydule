package com.example.gradule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The privacy policy, readable inside the app. Keep in step with docs/PRIVACY.md. */
private val policy: List<Pair<String, String>> = listOf(
    "Your data stays on your phone" to "Your calendar events, settings and reminders are stored only on your device. " +
        "Skydule reads and writes the calendars already on your phone. There are no accounts, no ads and no " +
        "analytics, and we don't run any servers.",
    "What is sent online, only when you use it" to "Weather: your approximate location is sent to a weather service " +
        "(Open-Meteo) to get the forecast. It isn't stored.\n\n" +
        "Place suggestions: text you type in an event's Location field is sent to a place-search service to " +
        "suggest places.",
    "Permissions" to "Calendar: to show, create, edit and delete your events.\n" +
        "Approximate location (optional): for local weather.\n" +
        "Notifications and alarms: to show reminders on time.\n" +
        "Internet: for the weather and place suggestions above.",
    "Sharing and your choices" to "We never sell or share your data. You can turn off any permission at any time, " +
        "and uninstalling Skydule removes everything it stored. Your events stay in your calendar accounts.",
    "Contact" to "getskydule@gmail.com",
)

@Composable
fun PrivacyScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(SettingsBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = White)
            }
            Text(
                "Privacy policy",
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                color = White,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            for ((heading, body) in policy) {
                Text(heading, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = White)
                Spacer(Modifier.height(6.dp))
                Text(body, fontSize = 15.sp, lineHeight = 22.sp, color = White.copy(alpha = 0.8f))
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}
