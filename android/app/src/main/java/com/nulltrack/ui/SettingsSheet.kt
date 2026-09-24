package com.nulltrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulltrack.data.ScheduleConfig
import com.nulltrack.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    schedule: ScheduleConfig,
    onDismiss: () -> Unit,
    onSaveSchedule: (ScheduleConfig) -> Unit,
    onPauseHours: (Int) -> Unit,
    onPauseToday: () -> Unit,
    onResumeNow: () -> Unit
) {
    var enabled by remember(schedule) { mutableStateOf(schedule.enabled) }

    // Plage Matin (Meudon ➔ Paris)
    var morningEnabled by remember(schedule) { mutableStateOf(schedule.morningEnabled) }
    var morningStartHour by remember(schedule) { mutableStateOf(schedule.morningStartHour) }
    var morningStartMinute by remember(schedule) { mutableStateOf(schedule.morningStartMinute) }
    var morningEndHour by remember(schedule) { mutableStateOf(schedule.morningEndHour) }
    var morningEndMinute by remember(schedule) { mutableStateOf(schedule.morningEndMinute) }

    // Plage Soir (Paris ➔ Meudon)
    var eveningEnabled by remember(schedule) { mutableStateOf(schedule.eveningEnabled) }
    var eveningStartHour by remember(schedule) { mutableStateOf(schedule.eveningStartHour) }
    var eveningStartMinute by remember(schedule) { mutableStateOf(schedule.eveningStartMinute) }
    var eveningEndHour by remember(schedule) { mutableStateOf(schedule.eveningEndHour) }
    var eveningEndMinute by remember(schedule) { mutableStateOf(schedule.eveningEndMinute) }

    var activeDays by remember(schedule) { mutableStateOf(schedule.activeDays) }
    var frequencyMinutes by remember(schedule) { mutableStateOf(schedule.frequencyMinutes) }

    val daysOfWeek = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceLight,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Titre et bouton fermer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = TransilienN,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Paramètres de surveillance",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFEEEEEE))

            // 1. Activation globale
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Surveillance active",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = if (enabled) "Les alertes sont activées" else "Toutes les alertes sont coupées",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TransilienN
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Section Pause / Snooze
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (schedule.isPaused()) Color(0xFFFFF3E0) else BackgroundLight
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (schedule.isPaused()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = Color(0xFFE65100)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "En pause jusqu'à ${schedule.getPausedRemainingText() ?: ""}",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE65100),
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onResumeNow,
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reprendre la surveillance maintenant")
                        }
                    } else {
                        Text(
                            text = "⏸️ Mettre en veille temporaire (Ne pas déranger) :",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onPauseHours(1) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("1 heure", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { onPauseHours(3) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("3 heures", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onPauseToday,
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("Aujourd'hui", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Jours de la semaine
            Text(
                text = "📅 Jours surveillés (aucun appel API les autres jours) :",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                daysOfWeek.forEachIndexed { index, label ->
                    val isSelected = activeDays.contains(index)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            activeDays = if (isSelected) {
                                activeDays.filter { it != index }
                            } else {
                                (activeDays + index).sorted()
                            }
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TransilienN,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Plage Matin (Meudon ➔ Paris)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundLight)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌅 Matin : Meudon ➔ Paris-Montparnasse",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Switch(
                            checked = morningEnabled,
                            onCheckedChange = { morningEnabled = it },
                            modifier = Modifier.scale(0.85f),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TransilienN)
                        )
                    }

                    if (morningEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Début :", fontSize = 12.sp, color = TextSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TimeStepper(value = morningStartHour, min = 5, max = 13, label = "h") { morningStartHour = it }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    TimeStepper(value = morningStartMinute, min = 0, max = 55, step = 5, label = "m") { morningStartMinute = it }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fin :", fontSize = 12.sp, color = TextSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TimeStepper(value = morningEndHour, min = morningStartHour, max = 14, label = "h") { morningEndHour = it }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    TimeStepper(value = morningEndMinute, min = 0, max = 55, step = 5, label = "m") { morningEndMinute = it }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Plage Soir (Paris ➔ Meudon)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundLight)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌆 Soir : Paris-Montparnasse ➔ Meudon",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Switch(
                            checked = eveningEnabled,
                            onCheckedChange = { eveningEnabled = it },
                            modifier = Modifier.scale(0.85f),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TransilienN)
                        )
                    }

                    if (eveningEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Début :", fontSize = 12.sp, color = TextSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TimeStepper(value = eveningStartHour, min = 15, max = 22, label = "h") { eveningStartHour = it }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    TimeStepper(value = eveningStartMinute, min = 0, max = 55, step = 5, label = "m") { eveningStartMinute = it }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fin :", fontSize = 12.sp, color = TextSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TimeStepper(value = eveningEndHour, min = eveningStartHour, max = 23, label = "h") { eveningEndHour = it }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    TimeStepper(value = eveningEndMinute, min = 0, max = 55, step = 5, label = "m") { eveningEndMinute = it }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 6. Fréquence
            Text(
                text = "📡 Fréquence des vérifications :",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(3, 5, 10, 15).forEach { min ->
                    val isSelected = frequencyMinutes == min
                    FilterChip(
                        selected = isSelected,
                        onClick = { frequencyMinutes = min },
                        label = { Text("${min} min${if (min == 3) " ⭐" else ""}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TransilienN,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Text(
                text = "Les appels API PRIM ne seront exécutés que toutes les $frequencyMinutes minutes pendant vos fenêtres actives (matin, soir ou déclencheur retour).",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Bouton Enregistrer
            Button(
                onClick = {
                    val updated = schedule.copy(
                        enabled = enabled,
                        morningEnabled = morningEnabled,
                        morningStartHour = morningStartHour,
                        morningStartMinute = morningStartMinute,
                        morningEndHour = morningEndHour,
                        morningEndMinute = morningEndMinute,
                        eveningEnabled = eveningEnabled,
                        eveningStartHour = eveningStartHour,
                        eveningStartMinute = eveningStartMinute,
                        eveningEndHour = eveningEndHour,
                        eveningEndMinute = eveningEndMinute,
                        activeDays = activeDays,
                        frequencyMinutes = frequencyMinutes
                    )
                    onSaveSchedule(updated)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TransilienN)
            ) {
                Text("Enregistrer les modifications", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.size((scale * 52).dp, (scale * 32).dp)
)

@Composable
fun TimeStepper(
    value: Int,
    min: Int,
    max: Int,
    step: Int = 1,
    label: String,
    onValueChange: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            IconButton(
                onClick = { if (value - step >= min) onValueChange(value - step) },
                modifier = Modifier.size(24.dp)
            ) {
                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text(
                text = String.format(Locale.FRANCE, "%02d%s", value, label),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(
                onClick = { if (value + step <= max) onValueChange(value + step) },
                modifier = Modifier.size(24.dp)
            ) {
                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
