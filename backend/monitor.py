import logging
from datetime import datetime, time, timezone
from typing import Any, Dict, List, Optional, Tuple
import requests

from config import (
    ACTIVE_DAYS,
    DESTINATION_FILTER,
    END_HOUR,
    END_MINUTE,
    FORCE_CHECK,
    LINE_REF,
    MONITORING_REF,
    PRIM_API_KEY,
    PRIM_API_URL,
    START_HOUR,
    START_MINUTE,
    TIMEZONE,
    WINDOW_END,
    WINDOW_START,
)

logger = logging.getLogger("null-track.monitor")


def is_within_monitoring_window(
    schedule: Optional[Dict[str, Any]] = None,
    dt: Optional[datetime] = None,
) -> Tuple[bool, str]:
    """
    Vérifie si la date/heure actuelle respecte tous les critères de surveillance :
    1. Surveillance globale activée (enabled)
    2. Période de mise en veille / pause (paused_until)
    3. Jour de la semaine actif (active_days)
    4. Plage horaire (start_hour:start_minute à end_hour:end_minute)
    5. Intervalle de fréquence (frequency_minutes)

    Retourne (is_active: bool, reason: str).
    """
    if FORCE_CHECK:
        logger.info("FORCE_CHECK est activé : vérification forcée hors plage.")
        return True, "FORCE_CHECK actif"

    now = dt or datetime.now(TIMEZONE)
    current_day = now.weekday()
    current_time = now.time()

    cfg = schedule or {}

    # 1. Vérification interrupteur principal
    if not cfg.get("enabled", True):
        logger.info("Surveillance désactivée par l'utilisateur.")
        return False, "Surveillance désactivée dans l'application"

    # 2. Vérification de la pause temporaire (Snooze)
    paused_until = cfg.get("paused_until")
    if paused_until:
        try:
            if isinstance(paused_until, (int, float)):
                pause_epoch = paused_until / 1000.0 if paused_until > 1e11 else float(paused_until)
                pause_dt = datetime.fromtimestamp(pause_epoch, tz=timezone.utc)
            elif isinstance(paused_until, str):
                pause_dt = datetime.fromisoformat(paused_until.replace("Z", "+00:00"))
            else:
                pause_dt = paused_until

            if now.astimezone(timezone.utc) < pause_dt.astimezone(timezone.utc):
                pause_str = pause_dt.astimezone(TIMEZONE).strftime("%H:%M")
                logger.info(f"Notifications en pause jusqu'à {pause_str}.")
                return False, f"Notifications en pause jusqu'à {pause_str}"
        except Exception as e:
            logger.warning(f"Erreur lors de la vérification de paused_until ({paused_until}): {e}")

    # 3. Vérification des jours actifs choisis par l'utilisateur
    active_days = cfg.get("active_days")
    if active_days is None:
        active_days = ACTIVE_DAYS
    if current_day not in active_days:
        day_name = now.strftime('%A')
        logger.info(
            f"Jour non surveillé ({day_name}, index {current_day}). "
            f"Jours actifs : {active_days}"
        )
        return False, f"Jour non surveillé ({day_name})"

    # 4. Vérification de la plage horaire définie
    start_h = cfg.get("start_hour", START_HOUR)
    start_m = cfg.get("start_minute", START_MINUTE)
    end_h = cfg.get("end_hour", END_HOUR)
    end_m = cfg.get("end_minute", END_MINUTE)

    w_start = time(int(start_h), int(start_m))
    w_end = time(int(end_h), int(end_m))

    if not (w_start <= current_time <= w_end):
        logger.info(
            f"Heure actuelle ({current_time.strftime('%H:%M:%S')}) en dehors de la plage "
            f"({w_start.strftime('%H:%M')} - {w_end.strftime('%H:%M')})."
        )
        return False, f"En dehors de la plage horaire ({w_start.strftime('%H:%M')} - {w_end.strftime('%H:%M')})"

    # 5. Vérification de l'intervalle de fréquence
    freq_min = int(cfg.get("frequency_minutes", 3))
    last_check = cfg.get("last_check_timestamp")
    if last_check and freq_min > 0:
        elapsed_sec = now.timestamp() - float(last_check)
        min_interval_sec = (freq_min * 60) - 20  # Tolérance de 20s
        if elapsed_sec < min_interval_sec:
            remaining = int(min_interval_sec - elapsed_sec)
            return False, f"Fréquence de {freq_min} min respectée (prochain appel dans ~{remaining}s)"

    return True, "OK"


def fetch_stop_monitoring(
    monitoring_ref: str = MONITORING_REF,
    api_key: str = PRIM_API_KEY,
    line_ref: Optional[str] = LINE_REF,
) -> Dict[str, Any]:
    """
    Interroge l'API PRIM SIRI Lite (stop-monitoring) pour un point d'arrêt donné.
    """
    if not api_key:
        raise ValueError(
            "PRIM_API_KEY est manquante. Veuillez définir la variable d'environnement PRIM_API_KEY."
        )

    headers = {
        "apiKey": api_key,
        "Accept": "application/json",
    }
    params: Dict[str, str] = {
        "MonitoringRef": monitoring_ref,
    }
    if line_ref:
        params["LineRef"] = line_ref

    response = requests.get(PRIM_API_URL, headers=headers, params=params, timeout=10)
    response.raise_for_status()
    return response.json()


def is_train_cancelled(call: Dict[str, Any], journey: Dict[str, Any]) -> bool:
    """
    Détecte si un train ou l'arrêt de ce train est supprimé/annulé.
    """
    # Vérification du statut de départ/arrivée dans MonitoredCall
    dep_status = str(call.get("DepartureStatus", "")).lower()
    arr_status = str(call.get("ArrivalStatus", "")).lower()
    cancelled_statuses = {"cancelled", "supprime", "supprimé", "cancelledstop"}

    if dep_status in cancelled_statuses or arr_status in cancelled_statuses:
        return True

    # Vérification des drapeaux d'annulation SIRI
    if call.get("Cancellation") is True or journey.get("Cancellation") is True:
        return True

    return False


def parse_aimed_time(iso_str: Optional[str]) -> str:
    """Formate une date ISO en heure locale HH:MM."""
    if not iso_str:
        return "--:--"
    try:
        dt = datetime.fromisoformat(iso_str.replace("Z", "+00:00"))
        local_dt = dt.astimezone(TIMEZONE)
        return local_dt.strftime("%H:%M")
    except Exception:
        return iso_str[-8:-3] if len(iso_str) >= 8 else iso_str


def extract_cancelled_trains(data: Dict[str, Any]) -> List[Dict[str, Any]]:
    """
    Parcourt la réponse JSON SIRI Lite et extrait les trains annulés
    correspondant à la direction souhaitée (ex. Paris-Montparnasse).
    """
    cancelled_trains = []

    try:
        siri = data.get("Siri", {})
        service_delivery = siri.get("ServiceDelivery", {})
        stop_deliveries = service_delivery.get("StopMonitoringDelivery", [])

        if not stop_deliveries:
            return []

        visits = stop_deliveries[0].get("MonitoredStopVisit", [])

        for visit in visits:
            journey = visit.get("MonitoredVehicleJourney", {})
            call = journey.get("MonitoredCall", {})

            # Vérification destination
            destination_names = [
                d.get("value", "") for d in journey.get("DestinationName", [])
            ]
            destination_str = " ".join(destination_names)

            # Si un filtre de destination est configuré (ex. "Montparnasse")
            if DESTINATION_FILTER and DESTINATION_FILTER.lower() not in destination_str.lower():
                continue

            # Vérification de l'annulation
            if is_train_cancelled(call, journey):
                journey_ref = (
                    journey.get("VehicleJourneyRef", {}).get("value")
                    or journey.get("FramedVehicleJourneyRef", {}).get("DatedVehicleJourneyRef")
                    or visit.get("ItemIdentifier", "")
                )

                # Code mission (ex. ROPO, POMA, etc.)
                notes = [n.get("value", "") for n in journey.get("JourneyNote", [])]
                mission_code = notes[0] if notes else ""
                if not mission_code and journey_ref:
                    mission_code = str(journey_ref).split(":")[-1]

                aimed_dep_iso = call.get("AimedDepartureTime") or call.get("AimedArrivalTime")
                dep_time_display = parse_aimed_time(aimed_dep_iso)

                # Date du jour pour déduplication unique (ex. 2026-09-24_ROPO_08:12)
                today_str = datetime.now(TIMEZONE).strftime("%Y-%m-%d")
                train_id = f"{today_str}_{journey_ref or mission_code}_{dep_time_display}"

                cancelled_trains.append({
                    "id": train_id,
                    "journey_ref": journey_ref,
                    "mission_code": mission_code,
                    "departure_time": dep_time_display,
                    "aimed_time_iso": aimed_dep_iso,
                    "destination": destination_str or "Paris-Montparnasse",
                    "stop_name": "Meudon",
                    "status": "ANNULÉ",
                })

    except Exception as e:
        logger.error(f"Erreur lors du parsing des données SIRI Lite: {e}", exc_info=True)

    return cancelled_trains
