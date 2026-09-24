package com.nulltrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulltrack.data.DepartureStatus
import com.nulltrack.data.ScheduleConfig
import com.nulltrack.data.TrainDeparture
import com.nulltrack.location.CommuteDirection
import com.nulltrack.location.LocationHelper
import com.nulltrack.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    schedule: ScheduleConfig,
    departures: List<TrainDeparture>,
    lastUpdatedDepartures: String?,
    isDeparturesLoading: Boolean,
    onSettingsClick: () -> Unit,
    onRefreshDepartures: () -> Unit,
    onTriggerQuickMonitoring: (durationMinutes: Int, direction: String) -> Unit,
    onCancelQuickMonitoring: () -> Unit
) {
    val context = LocalContext.current
    val isMonitoringActive = schedule.isMonitoringActiveNow()

    // Détection de position de l'utilisateur
    val detection = remember { LocationHelper.detectCommuteDirection(context) }
    var overrideDirection by remember { mutableStateOf<CommuteDirection?>(null) }
    val effectiveDirection = overrideDirection ?: detection.direction

    // Direction active lors de la surveillance
    val activeDirectionCode = schedule.quickMonitoringDirection ?: effectiveDirection.code
    var selectedFilter by remember(activeDirectionCode, isMonitoringActive) {
        mutableStateOf(activeDirectionCode)
    }

    val filteredDepartures = remember(departures, selectedFilter) {
        when (selectedFilter) {
            "TO_PARIS" -> departures.filter {
                it.destination.contains("Montparnasse", ignoreCase = true) ||
                it.destination.contains("Paris", ignoreCase = true)
            }
            "TO_MEUDON" -> departures.filter {
                !it.destination.contains("Montparnasse", ignoreCase = true) &&
                !it.destination.contains("Paris", ignoreCase = true)
            }
            else -> departures
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Badge Ligne N
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TransilienN),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "N",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Null-Track",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Options & Paramètres",
                            tint = if (schedule.isPaused()) Color(0xFFE65100) else TransilienN,
                            modifier = Modifier.size(26.dp)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
        ) {
            // ==========================================
            // CARTE DE CONTRÔLE DE LA SURVEILLANCE
            // ==========================================
            item {
                SurveillanceControlCard(
                    schedule = schedule,
                    isMonitoringActive = isMonitoringActive,
                    locationLabel = detection.locationLabel,
                    effectiveDirection = effectiveDirection,
                    onToggleOverrideDirection = {
                        overrideDirection = if (effectiveDirection == CommuteDirection.TO_PARIS) {
                            CommuteDirection.TO_MEUDON
                        } else {
                            CommuteDirection.TO_PARIS
                        }
                    },
                    onTriggerQuickMonitoring = onTriggerQuickMonitoring,
                    onCancelQuickMonitoring = onCancelQuickMonitoring
                )
            }

            // ==========================================
            // PROCHAINS DÉPARTS
            // ==========================================
            // En-tête des prochains départs
            item {
                val directionHeader = if (activeDirectionCode == "TO_PARIS") "Meudon ➔ Paris" else "Paris ➔ Meudon"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Prochains départs ($directionHeader)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Gare de Meudon • Mis à jour : ${lastUpdatedDepartures ?: "Chargement..."}",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }

                        IconButton(onClick = onRefreshDepartures, enabled = !isDeparturesLoading) {
                            if (isDeparturesLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = TransilienN,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Rafraîchir les départs",
                                    tint = TransilienN,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Badges récapitulatifs d'état
                item {
                    val cancelledCount = departures.count { it.status == DepartureStatus.CANCELLED }
                    val delayedCount = departures.count { it.status == DepartureStatus.DELAYED }
                    val onTimeCount = departures.count { it.status == DepartureStatus.ON_TIME }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (cancelledCount > 0) {
                            StatusSummaryChip(
                                count = cancelledCount,
                                label = "Annulé${if (cancelledCount > 1) "s" else ""}",
                                containerColor = Color(0xFFFFEBEE),
                                textColor = Color(0xFFC62828)
                            )
                        }
                        if (delayedCount > 0) {
                            StatusSummaryChip(
                                count = delayedCount,
                                label = "En retard",
                                containerColor = Color(0xFFFFF3E0),
                                textColor = Color(0xFFE65100)
                            )
                        }
                        StatusSummaryChip(
                            count = onTimeCount,
                            label = "À l'heure",
                            containerColor = Color(0xFFE8F5E9),
                            textColor = Color(0xFF2E7D32)
                        )
                    }
                }

                // Filtres de direction pour affiner si besoin
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == "TO_PARIS",
                            onClick = { selectedFilter = "TO_PARIS" },
                            label = { Text("Vers Paris", fontSize = 14.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TransilienN,
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = selectedFilter == "TO_MEUDON",
                            onClick = { selectedFilter = "TO_MEUDON" },
                            label = { Text("Vers Banlieue", fontSize = 14.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TransilienN,
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = selectedFilter == "ALL",
                            onClick = { selectedFilter = "ALL" },
                            label = { Text("Tous les trains", fontSize = 14.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TransilienN,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Liste des cartes de trains
                if (filteredDepartures.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceLight)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsTransit,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Aucun train prévu dans cette direction pour le moment.",
                                    fontSize = 15.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(filteredDepartures, key = { it.id }) { departure ->
                        DepartureCard(departure = departure)
                    }
                }
        }
    }
}

/**
 * Carte de contrôle unique pour démarrer et arrêter la surveillance.
 */
@Composable
fun SurveillanceControlCard(
    schedule: ScheduleConfig,
    isMonitoringActive: Boolean,
    locationLabel: String,
    effectiveDirection: CommuteDirection,
    onToggleOverrideDirection: () -> Unit,
    onTriggerQuickMonitoring: (Int, String) -> Unit,
    onCancelQuickMonitoring: () -> Unit
) {
    val durationMin = schedule.quickMonitoringDurationMinutes
    val durationLabel = if (durationMin >= 60) "${durationMin / 60}h" else "${durationMin} min"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMonitoringActive) Color(0xFFE8F5E9) else SurfaceLight
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isMonitoringActive) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isMonitoringActive) SuccessGreen else Color(0xFFE3F2FD)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMonitoringActive) Icons.Default.DirectionsTransit else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (isMonitoringActive) Color.White else TransilienN,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isMonitoringActive)
                                "Surveillance Active 🟢"
                            else
                                "Surveillance Géolocalisée",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (isMonitoringActive) SuccessGreen else TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isMonitoringActive)
                                "Sens : ${schedule.getQuickMonitoringDirectionText()} • Fin à ${schedule.getQuickMonitoringRemainingText() ?: "--:--"}"
                            else
                                "📍 $locationLabel • Sens : ${effectiveDirection.label}",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isMonitoringActive) {
                OutlinedButton(
                    onClick = onCancelQuickMonitoring,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Arrêter la surveillance", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bouton principal pour démarrer la surveillance
                    Button(
                        onClick = { onTriggerQuickMonitoring(durationMin, effectiveDirection.code) },
                        colors = ButtonDefaults.buttonColors(containerColor = TransilienN),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Démarrer ($durationLabel) • ${effectiveDirection.label}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Bouton pour inverser le sens
                    IconButton(
                        onClick = onToggleOverrideDirection,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEEEEEE))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Inverser le sens",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Message affiché lorsque la surveillance est inactive.
 * Aucun bouton n'est proposé : le seul moyen d'afficher les trains est d'activer la surveillance.
 */
@Composable
fun SurveillanceInactiveCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0F4F8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsTransit,
                    contentDescription = null,
                    tint = TransilienN,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Surveillance arrêtée",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Déclenchez la surveillance ci-dessus pour afficher la liste des prochains départs en temps réel dans le sens de votre trajet.",
                fontSize = 15.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}
