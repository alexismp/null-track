package com.nulltrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulltrack.data.ScheduleConfig
import com.nulltrack.data.TrainAlert
import com.nulltrack.location.CommuteDirection
import com.nulltrack.location.LocationHelper
import com.nulltrack.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    alerts: List<TrainAlert>,
    isSubscribed: Boolean,
    schedule: ScheduleConfig,
    onTestAlertClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onViewDeparturesClick: () -> Unit,
    onTriggerQuickMonitoring: (durationMinutes: Int, direction: String) -> Unit = { _, _ -> },
    onCancelQuickMonitoring: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Badge Ligne N
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(TransilienN),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "N",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Null-Track",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Paramètres de surveillance",
                            tint = if (schedule.isPaused()) Color(0xFFE65100) else TransilienN
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceLight
                )
            )
        },
        containerColor = BackgroundLight
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Carte Déclencheur Ponctuel Géolocalisé (Widget & In-App)
            QuickMonitoringLocationCard(
                schedule = schedule,
                onTriggerQuickMonitoring = onTriggerQuickMonitoring,
                onCancelQuickMonitoring = onCancelQuickMonitoring
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Carte de statut de la surveillance
            MonitoringStatusCard(
                isSubscribed = isSubscribed,
                schedule = schedule,
                onSettingsClick = onSettingsClick
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bouton principal pour consulter tous les départs
            Button(
                onClick = onViewDeparturesClick,
                colors = ButtonDefaults.buttonColors(containerColor = TransilienN),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsTransit,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🚆 Voir tous les prochains départs (Meudon)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions : Bouton de test et effacement
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTestAlertClick,
                    colors = ButtonDefaults.buttonColors(containerColor = TransilienN),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tester une alerte push")
                }

                if (alerts.isNotEmpty()) {
                    IconButton(onClick = onClearHistoryClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Effacer l'historique",
                            tint = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Titre de section
            Text(
                text = "Historique des alertes (Annulations & Retards)",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (alerts.isEmpty()) {
                EmptyStateCard()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(alerts, key = { it.id }) { alert ->
                        AlertCard(alert = alert)
                    }
                }
            }
        }
    }
}

/**
 * Carte intelligente tenant compte de la localisation de l'utilisateur :
 * - Si à Paris -> Sens Paris ➔ Banlieue (Paris ➔ Meudon)
 * - Si proche de Meudon -> Sens Banlieue ➔ Paris (Meudon ➔ Paris)
 * Active la surveillance pour une durée paramétrable (ex: 1 heure)
 */
@Composable
fun QuickMonitoringLocationCard(
    schedule: ScheduleConfig,
    onTriggerQuickMonitoring: (Int, String) -> Unit,
    onCancelQuickMonitoring: () -> Unit
) {
    val context = LocalContext.current
    val isQuickActive = schedule.isQuickMonitoringActive()

    // Détection en direct de la position
    val detection = remember { LocationHelper.detectCommuteDirection(context) }
    var overrideDirection by remember { mutableStateOf<CommuteDirection?>(null) }
    val effectiveDirection = overrideDirection ?: detection.direction

    val durationMin = schedule.quickMonitoringDurationMinutes
    val durationLabel = if (durationMin >= 60) "${durationMin / 60}h" else "${durationMin} min"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isQuickActive) Color(0xFFE8F5E9) else SurfaceLight
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isQuickActive) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isQuickActive) SuccessGreen else Color(0xFFE3F2FD)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isQuickActive) Icons.Default.DirectionsTransit else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (isQuickActive) Color.White else TransilienN,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isQuickActive)
                                "Surveillance Active 🟢 (${schedule.getQuickMonitoringDirectionText()})"
                            else
                                "Surveillance Rapide & Géolocalisée",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isQuickActive) SuccessGreen else TextPrimary
                        )
                        Text(
                            text = if (isQuickActive)
                                "Fin à ${schedule.getQuickMonitoringRemainingText() ?: "--:--"} • Annulations & Retards"
                            else
                                "📍 ${detection.locationLabel} • Sens : ${effectiveDirection.label}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isQuickActive) {
                OutlinedButton(
                    onClick = onCancelQuickMonitoring,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Arrêter la surveillance", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bouton principal d'activation (sens détecté automatiquement)
                    Button(
                        onClick = { onTriggerQuickMonitoring(durationMin, effectiveDirection.code) },
                        colors = ButtonDefaults.buttonColors(containerColor = TransilienN),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Activer ($durationLabel) • ${effectiveDirection.label}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Bouton pour inverser le sens si souhaité
                    IconButton(
                        onClick = {
                            overrideDirection = if (effectiveDirection == CommuteDirection.TO_PARIS)
                                CommuteDirection.TO_MEUDON
                            else
                                CommuteDirection.TO_PARIS
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Inverser le sens",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonitoringStatusCard(
    isSubscribed: Boolean,
    schedule: ScheduleConfig,
    onSettingsClick: () -> Unit
) {
    val isPaused = schedule.isPaused()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Surveillance Programmée",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                // Badge Actif / En pause / Désactivé
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint, label) = when {
                        !schedule.enabled -> Triple(Icons.Default.Warning, Color(0xFFD32F2F), "Désactivé")
                        isPaused -> Triple(Icons.Default.PauseCircle, Color(0xFFE65100), "En pause")
                        isSubscribed -> Triple(Icons.Default.CheckCircle, SuccessGreen, "Actif")
                        else -> Triple(Icons.Default.CheckCircle, TextSecondary, "Connexion...")
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        color = tint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFEEEEEE))

            if (isPaused) {
                Text(
                    text = "⏸️ En pause jusqu'à ${schedule.getPausedRemainingText() ?: ""}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE65100)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Direction Matin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🌅 Matin (Meudon ➔ Paris) :",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = if (schedule.morningEnabled) schedule.formatMorningTimeRange() else "Désactivé",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (schedule.morningEnabled) TextPrimary else TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Direction Soir
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🌆 Soir (Paris ➔ Meudon) :",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = if (schedule.eveningEnabled) schedule.formatEveningTimeRange() else "Désactivé",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (schedule.eveningEnabled) TextPrimary else TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "📅 Jours actifs : ${schedule.formatActiveDays()}",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "📡 Alertes : Annulations ${if (schedule.notifyDelays) "+ Retards (≥ ${schedule.minDelayMinutes}m)" else ""}",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 5.dp)
            ) {
                Text("Modifier les plages horaires, jours & durée du widget", fontSize = 12.sp)
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
                        fontSize = 16.sp,
                        color = badgeBg
                    )
                    Text(
                        text = badgeText,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = badgeBg
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Mission : ${alert.missionCode} • $directionLabel",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Terminus : ${alert.destination}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Alerte reçue le $formattedDate",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Trafic normal",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Les trains circulent normalement. Aucune annulation ni retard significatif n'a été signalé pour vos trajets surveillés.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}
