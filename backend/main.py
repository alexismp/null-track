import logging
import os
from typing import Any, Dict
import functions_framework
from flask import Request, jsonify

from fcm_client import send_cancellation_alert
from firestore_client import is_train_notified, mark_train_as_notified
from monitor import extract_cancelled_trains, fetch_stop_monitoring, is_within_monitoring_window

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("null-track.main")


def run_cancellation_check(force: bool = False) -> Dict[str, Any]:
    """
    Exécute le cycle complet de vérification :
    1. Validation de la plage horaire
    2. Appel API IDFM PRIM
    3. Détection des suppressions
    4. Déduplication et envoi des notifications FCM
    """
    if not force and not is_within_monitoring_window():
        return {
            "status": "skipped",
            "message": "En dehors de la plage horaire ou des jours de surveillance.",
            "alerts_sent": 0,
        }

    try:
        data = fetch_stop_monitoring()
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


@functions_framework.http
def check_trains_http(request: Request):
    """
    Point d'entrée HTTP pour Google Cloud Functions (Gen2) ou Cloud Run,
    déclenché par Cloud Scheduler.
    """
    # Possibilité de forcer la vérification via ?force=true pour tests manuels
    force_param = request.args.get("force", "").lower() in ("true", "1", "yes")
    result = run_cancellation_check(force=force_param)

    status_code = 200 if result.get("status") in ("success", "skipped") else 500
    return jsonify(result), status_code


if __name__ == "__main__":
    # Permet de tester directement : python main.py
    import json
    res = run_cancellation_check(force=True)
    print(json.dumps(res, indent=2, ensure_ascii=False))
