import logging
from typing import Any, Dict
try:
    import firebase_admin
    from firebase_admin import messaging
    _HAS_FIREBASE = True
except ImportError:
    _HAS_FIREBASE = False
    firebase_admin = None
    messaging = None

from config import FCM_TOPIC, FIREBASE_PROJECT_ID

logger = logging.getLogger("null-track.fcm")

_firebase_initialized = False


def init_firebase():
    """Initialise Firebase Admin SDK avec les identifiants par défaut GCP ou Service Account."""
    global _firebase_initialized
    if not _HAS_FIREBASE:
        logger.info("Module firebase_admin non installé. Mode simulation actif.")
        return False
    if _firebase_initialized:
        return True

    try:
        if not firebase_admin._apps:
            options = {"projectId": FIREBASE_PROJECT_ID} if FIREBASE_PROJECT_ID else None
            firebase_admin.initialize_app(options=options)
        _firebase_initialized = True
        logger.info("Firebase Admin SDK initialisé avec succès.")
        return True
    except Exception as e:
        logger.warning(f"Impossible d'initialiser Firebase Admin SDK: {e}")
        return False


def send_cancellation_alert(train: Dict[str, Any], topic: str = FCM_TOPIC) -> bool:
    """
    Envoie une notification Push haute priorité sur le topic FCM spécifié.
    L'application Android recevra l'alerte même en arrière-plan ou en veille.
    """
    if not init_firebase():
        logger.warning(
            f"[SIMULATION PUSH] (Firebase non configuré) Alerte non envoyée pour le train {train.get('id')}"
        )
        return False

    departure = train.get("departure_time", "--:--")
    mission = train.get("mission_code", "Ligne N")
    stop = train.get("stop_name", "Meudon")
    dest = train.get("destination", "Paris-Montparnasse")
    direction_code = train.get("direction_code", "TO_PARIS")

    if direction_code == "TO_MEUDON":
        title = f"⚠️ Train retour supprimé : {mission} ({departure})"
        body = f"Le train de {departure} (sens Paris ➔ Meudon, vers {dest}) est supprimé."
    else:
        title = f"⚠️ Train supprimé : {mission} ({departure})"
        body = f"Le train de {departure} au départ de Meudon vers {dest} est supprimé."

    message = messaging.Message(
        topic=topic,
        notification=messaging.Notification(
            title=title,
            body=body,
        ),
        android=messaging.AndroidConfig(
            priority="high",
            notification=messaging.AndroidNotification(
                channel_id="TRAIN_ALERTS",
                sound="default",
                priority="max",
                default_vibrate_timings=True,
            ),
        ),
        data={
            "train_id": str(train.get("id", "")),
            "mission_code": str(mission),
            "departure_time": str(departure),
            "stop_name": str(stop),
            "destination": str(dest),
            "status": "ANNULÉ",
        },
    )

    try:
        response = messaging.send(message)
        logger.info(f"Notification FCM envoyée avec succès sur le topic '{topic}' (ID: {response})")
        return True
    except Exception as e:
        logger.error(f"Échec de l'envoi de la notification FCM: {e}")
        return False
