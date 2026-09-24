package com.nulltrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulltrack.data.TrainAlert
import com.nulltrack.ui.theme.*
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Tune
import com.nulltrack.data.ScheduleConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    alerts: List<TrainAlert>,
    isSubscribed: Boolean,
    schedule: ScheduleConfig,
    onTestAlertClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onViewDeparturesClick: () -> Unit
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

            // Carte de statut de la surveillance
            MonitoringStatusCard(
                isSubscribed = isSubscribed,
                schedule = schedule,
                onSettingsClick = onSettingsClick
            )

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(20.dp))

            // Titre de section
            Text(
                text = "Historique des alertes reçues",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Meudon ➔ Paris-Montparnasse",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
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

            Divider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFEEEEEE))

            if (isPaused) {
                Text(
                    text = "⏸️ En pause jusqu'à ${schedule.getPausedRemainingText() ?: ""}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE65100)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = "📅 Jours actifs : ${schedule.formatActiveDays()}",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "⏰ Plage horaire : ${schedule.formatTimeRange()}",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "📡 Fréquence : Toutes les ${schedule.frequencyMinutes} min (uniquement dans la plage)",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Text("Modifier les horaires, jours & mise en veille", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AlertCard(alert: TrainAlert) {
    val timeFormat = SimpleDateFormat("dd/MM à HH:mm", Locale.FRANCE)
    val formattedDate = timeFormat.format(Date(alert.receivedAtTimestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CancelledRedLight),
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
                    .background(CancelledRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
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
                        color = CancelledRed
                    )
                    Text(
                        text = "ANNULÉ",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = CancelledRed
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Mission : ${alert.missionCode} • ${alert.stopName} ➔ ${alert.destination}",
                    fontSize = 13.sp,
                    color = TextPrimary
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
            .padding(vertical = 24.dp),
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
                text = "Aucun train annulé",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Les trains circulent normalement ou aucune suppression n'a été signalée pour l'instant. Vous recevrez une notification sonore dès qu'une anomalie survient.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}
