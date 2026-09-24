package com.nulltrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulltrack.data.ScheduleConfig
import com.nulltrack.data.TrainAlert
import com.nulltrack.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    schedule: ScheduleConfig,
    alerts: List<TrainAlert> = emptyList(),
    isSubscribed: Boolean = true,
    onDismiss: () -> Unit,
    onSaveSchedule: (ScheduleConfig) -> Unit,
    onPauseHours: (Int) -> Unit,
    onPauseToday: () -> Unit,
    onResumeNow: () -> Unit,
    onTestAlertClick: () -> Unit = {},
    onClearHistoryClick: () -> Unit = {}
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
    var quickDuration by remember(schedule) { mutableStateOf(schedule.quickMonitoringDurationMinutes) }
    var notifyDelays by remember(schedule) { mutableStateOf(schedule.notifyDelays) }

    var showHistorySection by remember { mutableStateOf(false) }

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
                .padding(bottom = 36.dp)
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
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Options & Paramètres",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(24.dp))
                }
            }

            Divider(modifier = Modifier.padding(vertical = 14.dp), color = Color(0xFFEEEEEE))

            // ==========================================
            // 1. SURVEILLANCE PROGRAMMÉE
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundLight)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Surveillance Programmée",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )

                        val (statusIcon, statusTint, statusLabel) = when {
                            !enabled -> Triple(Icons.Default.Warning, Color(0xFFD32F2F), "Désactivée")
                            schedule.isPaused() -> Triple(Icons.Default.PauseCircle, Color(0xFFE65100), "En pause")
                            isSubscribed -> Triple(Icons.Default.CheckCircle, SuccessGreen, "Active")
                            else -> Triple(Icons.Default.CheckCircle, TextSecondary, "Connexion...")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = statusIcon, contentDescription = null, tint = statusTint, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = statusLabel, color = statusTint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Activer la surveillance automatique",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Surveille automatiquement les trains selon vos plages horaires et jours de travail",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp
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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 2. MISE EN VEILLE TEMPORAIRE (SNOOZE)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (schedule.isPaused()) Color(0xFFFFF3E0) else BackgroundLight
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (schedule.isPaused()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "En pause jusqu'à ${schedule.getPausedRemainingText() ?: ""}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onResumeNow,
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reprendre la surveillance", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "⏸️ Mettre en pause temporaire (Ne pas déranger) :",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onPauseHours(1) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("1 heure", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            OutlinedButton(
                                onClick = { onPauseHours(3) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("3 heures", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            OutlinedButton(
                                onClick = onPauseToday,
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("Aujourd'hui", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // 3. JOURS ACTIFS
            // ==========================================
            Text(
                text = "📅 Jours surveillés (aucun appel API les autres jours) :",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
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
                        label = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TransilienN,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // 4. PLAGE MATIN
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundLight)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌅 Matin : Meudon ➔ Paris-Montparnasse",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Switch(
                            checked = morningEnabled,
                            onCheckedChange = { morningEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TransilienN)
                        )
                    }

                    if (morningEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Début :", fontSize = 14.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TimeStepper(value = morningStartHour, min = 5, max = 13, label = "h") { morningStartHour = it }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TimeStepper(value = morningStartMinute, min = 0, max = 55, step = 5, label = "m") { morningStartMinute = it }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fin :", fontSize = 14.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TimeStepper(value = morningEndHour, min = morningStartHour, max = 14, label = "h") { morningEndHour = it }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TimeStepper(value = morningEndMinute, min = 0, max = 55, step = 5, label = "m") { morningEndMinute = it }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // 5. PLAGE SOIR
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundLight)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌆 Soir : Paris-Montparnasse ➔ Meudon",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Switch(
                            checked = eveningEnabled,
                            onCheckedChange = { eveningEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TransilienN)
                        )
                    }

                    if (eveningEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Début :", fontSize = 14.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TimeStepper(value = eveningStartHour, min = 15, max = 22, label = "h") { eveningStartHour = it }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TimeStepper(value = eveningStartMinute, min = 0, max = 55, step = 5, label = "m") { eveningStartMinute = it }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fin :", fontSize = 14.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TimeStepper(value = eveningEndHour, min = eveningStartHour, max = 23, label = "h") { eveningEndHour = it }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TimeStepper(value = eveningEndMinute, min = 0, max = 55, step = 5, label = "m") { eveningEndMinute = it }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // 6. PARAMÈTRES DU WIDGET & SURVEILLANCE RAPIDE
            // ==========================================
            Text(
                text = "⏱️ Durée par défaut de la surveillance ponctuelle :",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(30 to "30 min", 60 to "1 heure ⭐", 90 to "1h30", 120 to "2 heures").forEach { (duration, label) ->
                    val isSelected = quickDuration == duration
                    FilterChip(
                        selected = isSelected,
                        onClick = { quickDuration = duration },
                        label = { Text(label, fontSize = 14.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TransilienN,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // 7. NOTIFICATIONS DES RETARDS
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⏱️ Notifier les retards (≥ 5 min)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Alerter aussi en cas de train retardé en plus des suppressions fermes",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                    Switch(
                        checked = notifyDelays,
                        onCheckedChange = { notifyDelays = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TransilienN)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // 8. FRÉQUENCE DES VÉRIFICATIONS
            // ==========================================
            Text(
                text = "📡 Fréquence des vérifications :",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
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
                        label = { Text("${min} min${if (min == 3) " ⭐" else ""}", fontSize = 14.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TransilienN,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Text(
                text = "Les vérifications API sont exécutées toutes les $frequencyMinutes minutes pendant vos fenêtres de surveillance actives.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            // ==========================================
            // 9. TEST D'ALERTE PUSH (DÉPLACÉ HORS HOME SCREEN)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8FD))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = TransilienN,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Test d'alerte push",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Vérifiez que votre appareil reçoit bien les notifications sonores et vibratoires en générant une alerte test immédiate.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onTestAlertClick,
                        colors = ButtonDefaults.buttonColors(containerColor = TransilienN),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tester une alerte push maintenant", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // 10. HISTORIQUE DES ALERTES & NETTOYAGE
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Historique des alertes (${alerts.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                if (alerts.isNotEmpty()) {
                    TextButton(onClick = onClearHistoryClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFC62828)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Effacer", color = Color(0xFFC62828), fontSize = 14.sp)
                    }
                }
            }

            if (alerts.isEmpty()) {
                Text(
                    text = "Aucune alerte reçue récemment.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                TextButton(
                    onClick = { showHistorySection = !showHistorySection },
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = if (showHistorySection) "Masquer les détails" else "Afficher les ${alerts.size} alertes reçues",
                        fontSize = 14.sp,
                        color = TransilienN,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (showHistorySection) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        alerts.forEach { alert ->
                            AlertCard(alert = alert)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // ==========================================
            // BOUTON ENREGISTRER
            // ==========================================
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
                        frequencyMinutes = frequencyMinutes,
                        quickMonitoringDurationMinutes = quickDuration,
                        notifyDelays = notifyDelays
                    )
                    onSaveSchedule(updated)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TransilienN)
            ) {
                Text("Enregistrer les options", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun AlertCard(alert: TrainAlert) {
    val timeFormat = SimpleDateFormat("dd/MM à HH:mm", Locale.FRANCE)
    val formattedDate = timeFormat.format(Date(alert.receivedAtTimestamp))

    val isToParis = alert.destination.contains("Paris", ignoreCase = true) ||
            alert.destination.contains("Montparnasse", ignoreCase = true)
    val directionLabel = if (isToParis) "Meudon ➔ Paris" else "Paris ➔ Meudon"

    val isDelayed = alert.status.contains("RETARD", ignoreCase = true)

    val cardBg = if (isDelayed) Color(0xFFFFF3E0) else CancelledRedLight
    val badgeBg = if (isDelayed) Color(0xFFE65100) else CancelledRed
    val badgeText = if (isDelayed) "RETARDÉ" else "ANNULÉ"
    val icon = if (isDelayed) Icons.Default.Schedule else Icons.Default.Warning

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Train de ${alert.departureTime}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = badgeBg
                    )
                    Text(
                        text = badgeText,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = badgeBg
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Mission : ${alert.missionCode} • $directionLabel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Terminus : ${alert.destination}",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reçue le $formattedDate",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(
                onClick = { if (value - step >= min) onValueChange(value - step) },
                modifier = Modifier.size(30.dp)
            ) {
                Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text(
                text = String.format(Locale.FRANCE, "%02d%s", value, label),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            IconButton(
                onClick = { if (value + step <= max) onValueChange(value + step) },
                modifier = Modifier.size(30.dp)
            ) {
                Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}
