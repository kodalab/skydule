package com.kodalabs.skydule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Landing page, shown until calendar access is granted. "Get started" asks for that access.
 * If it was denied, it also offers the app's settings page.
 */
@Composable
fun LandingScreen(denied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Spacer(Modifier.height(24.dp))
            Text("SKYDULE", style = MaterialTheme.typography.labelLarge, color = White.copy(alpha = 0.7f))
            Spacer(Modifier.height(12.dp))
            Text(
                "Your calendars, under a living sky",
                style = MaterialTheme.typography.displayMedium,
                color = White,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Skydule shows the Google and on-phone calendars already on your phone. " +
                    "No accounts to create, no servers, nothing leaves the device.",
                color = White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Glass)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("•  Schedule, week and month in one place", color = White.copy(alpha = 0.9f))
                Text("•  Reminders that still fire when the app is closed", color = White.copy(alpha = 0.9f))
            }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (denied) {
                Text(
                    "Calendar access is off, so there's nothing to show yet.",
                    color = White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenSettings) { Text("Open app settings") }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text("Get started")
            }
        }
    }
}
