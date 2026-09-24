import logging
import os
from typing import Any, Dict
try:
    import functions_framework
    from flask import Request, jsonify
    _HAS_FRAMEWORK = True
except ImportError:
    _HAS_FRAMEWORK = False
    functions_framework = None
    Request = Any
    def jsonify(data):
        return data

from fcm_client import send_cancellation_alert
from firestore_client import (
    get_live_departures,
    get_monitoring_schedule,
    is_train_notified,
    mark_train_as_notified,
    save_live_departures,
    update_last_check_timestamp,
    update_monitoring_schedule,
)
from monitor import (
    extract_all_departures,
    extract_cancelled_trains,
    fetch_stop_monitoring,
    is_within_monitoring_window,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("null-track.main")


def run_cancellation_check(force: bool = False) -> Dict[str, Any]:
    """
    Exécute le cycle complet de vérification :
    1. Validation des critères dynamiques (jours choisis, plage horaire, pause/snooze, fréquence)
    2. Appel API IDFM PRIM (UNIQUEMENT si les critères sont validés)
    3. Détection des suppressions
    4. Déduplication et envoi des notifications FCM
    """
    schedule = get_monitoring_schedule()
    is_active, reason = is_within_monitoring_window(schedule=schedule)

    if not force and not is_active:
        return {
            "status": "skipped",
            "message": reason,
            "alerts_sent": 0,
            "schedule": {
                "enabled": schedule.get("enabled", True),
                "active_days": schedule.get("active_days"),
                "hours": f"{schedule.get('start_hour', 7):02d}h{schedule.get('start_minute', 0):02d} - {schedule.get('end_hour', 9):02d}h{schedule.get('end_minute', 30):02d}",
                "paused_until": schedule.get("paused_until"),
            }
        }

    try:
        # Met à jour le timestamp de la vérification pour respecter la fréquence
        update_last_check_timestamp()

        # Appel API PRIM garanti uniquement lors des jours/horaires demandés
        data = fetch_stop_monitoring()
        # Enregistre le tableau complet des départs pour consultation temps réel
        all_deps = extract_all_departures(data)
        save_live_departures(all_deps)

        cancelled_trains = extract_cancelled_trains(data)

        alerts_sent = 0
        skipped_already_notified = 0
        details = []

        for train in cancelled_trains:
            train_id = train["id"]
            if is_train_notified(train_id):
                skipped_already_notified += 1
                logger.info(f"Train {train_id} déjà notifié précédemment. Ignoré.")
                continue

            # Envoi de la notification push FCM
            success = send_cancellation_alert(train)
            if success:
                mark_train_as_notified(train)
                alerts_sent += 1
                details.append({
                    "train_id": train_id,
                    "departure": train["departure_time"],
                    "mission": train["mission_code"],
                })

        return {
            "status": "success",
            "cancelled_detected": len(cancelled_trains),
            "total_departures": len(all_deps),
            "alerts_sent": alerts_sent,
            "already_notified": skipped_already_notified,
            "notified_trains": details,
        }

    except Exception as e:
        logger.error(f"Erreur lors de l'exécution de la surveillance: {e}", exc_info=True)
        return {
            "status": "error",
            "message": str(e),
            "alerts_sent": 0,
        }


def check_trains_http(request: Request):
    """
    Point d'entrée HTTP pour Cloud Run functions,
    déclenché par Cloud Scheduler ou l'application Android.
    """
    action = request.args.get("action", "")

    # 1. Endpoint de lecture de configuration : GET ?action=get_config ou path /config
    if request.method == "GET" and (action == "get_config" or request.path.endswith("/config")):
        return jsonify(get_monitoring_schedule()), 200

    # 2. Endpoint de mise à jour de configuration : POST ?action=set_config ou path /config
    if request.method == "POST" and (action == "set_config" or request.path.endswith("/config")):
        body = request.get_json(silent=True) or {}
        updated = update_monitoring_schedule(body)
        return jsonify({"status": "success", "config": updated}), 200

    # 3. Endpoint pour consulter tous les départs : GET ?action=departures ou path /departures
    if request.method == "GET" and (action == "departures" or request.path.endswith("/departures")):
        fresh = request.args.get("fresh", "").lower() in ("true", "1", "yes")
        live = get_live_departures()
        if fresh or not live.get("departures"):
            try:
                raw_data = fetch_stop_monitoring()
                deps = extract_all_departures(raw_data)
                save_live_departures(deps)
                live = {
                    "status": "success",
                    "stop_name": "Meudon",
                    "departures": deps,
                    "updated_at": datetime.now(timezone.utc).isoformat()
                }
            except Exception as e:
                logger.error(f"Erreur récupération des départs : {e}")
                return jsonify({"status": "error", "message": str(e), "departures": []}), 500
        return jsonify(live), 200

    # 4. Cycle régulier de surveillance des trains
    force_param = request.args.get("force", "").lower() in ("true", "1", "yes")
    result = run_cancellation_check(force=force_param)

    status_code = 200 if result.get("status") in ("success", "skipped") else 500
    return jsonify(result), status_code


if functions_framework is not None:
    check_trains_http = functions_framework.http(check_trains_http)


if __name__ == "__main__":
    # Permet de tester directement : python main.py
    import json
    res = run_cancellation_check(force=True)
    print(json.dumps(res, indent=2, ensure_ascii=False))
