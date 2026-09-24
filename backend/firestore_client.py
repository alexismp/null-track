import logging
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Optional

from config import (
    ACTIVE_DAYS,
    DEFAULT_FREQUENCY_MINUTES,
    END_HOUR,
    END_MINUTE,
    FIRESTORE_COLLECTION,
    FIREBASE_PROJECT_ID,
    SCHEDULE_DOCUMENT,
    SETTINGS_COLLECTION,
    START_HOUR,
    START_MINUTE,
)

logger = logging.getLogger("null-track.firestore")

_db = None
_in_memory_cache = set()
_in_memory_schedule = {
    "enabled": True,
    "start_hour": START_HOUR,
    "start_minute": START_MINUTE,
    "end_hour": END_HOUR,
    "end_minute": END_MINUTE,
    "active_days": ACTIVE_DAYS,
    "frequency_minutes": DEFAULT_FREQUENCY_MINUTES,
    "paused_until": None,
    "last_check_timestamp": 0,
}


def get_firestore_client():
    """Initialise ou réutilise le client Google Cloud Firestore."""
    global _db
    if _db is not None:
        return _db

    try:
        from google.cloud import firestore

        if FIREBASE_PROJECT_ID:
            _db = firestore.Client(project=FIREBASE_PROJECT_ID)
        else:
            _db = firestore.Client()
        logger.info("Connexion à Cloud Firestore initialisée avec succès.")
        return _db
    except Exception as e:
        logger.warning(
            f"Firestore non disponible ({e}). Utilisation du cache mémoire local (non persistant)."
        )
        return None


def is_train_notified(train_id: str) -> bool:
    """
    Vérifie si une alerte pour ce train a déjà été enregistrée dans Firestore
    (ou dans le cache local si hors-ligne).
    """
    db = get_firestore_client()
    if db is None:
        return train_id in _in_memory_cache

    try:
        doc_ref = db.collection(FIRESTORE_COLLECTION).document(train_id)
        doc = doc_ref.get()
        return doc.exists
    except Exception as e:
        logger.error(f"Erreur lors de la lecture Firestore pour {train_id}: {e}")
        return train_id in _in_memory_cache


def mark_train_as_notified(train: Dict[str, Any]) -> None:
    """
    Enregistre le train comme notifié dans Firestore avec un horodatage
    et la date d'expiration pour la rétention automatique.
    """
    train_id = train["id"]
    _in_memory_cache.add(train_id)

    db = get_firestore_client()
    if db is None:
        return

    try:
        now = datetime.now(timezone.utc)
        doc_ref = db.collection(FIRESTORE_COLLECTION).document(train_id)
        doc_ref.set({
            "train_id": train_id,
            "journey_ref": train.get("journey_ref"),
            "mission_code": train.get("mission_code"),
            "departure_time": train.get("departure_time"),
            "destination": train.get("destination"),
            "stop_name": train.get("stop_name"),
            "notified_at": now,
            "expires_at": now + timedelta(days=2),  # Nettoyage automatique TTL
        })
        logger.info(f"Train {train_id} enregistré dans Firestore comme notifié.")
    except Exception as e:
        logger.error(f"Erreur lors de l'écriture Firestore pour {train_id}: {e}")


def get_monitoring_schedule() -> Dict[str, Any]:
    """
    Récupère la configuration dynamique de surveillance depuis Firestore (settings/monitoring_schedule).
    Retourne la configuration par défaut si le document n'existe pas encore.
    """
    global _in_memory_schedule
    db = get_firestore_client()
    if db is None:
        return _in_memory_schedule.copy()

    try:
        doc_ref = db.collection(SETTINGS_COLLECTION).document(SCHEDULE_DOCUMENT)
        doc = doc_ref.get()
        if doc.exists:
            data = doc.to_dict() or {}
            # Fusion avec les valeurs par défaut
            merged = _in_memory_schedule.copy()
            merged.update(data)
            _in_memory_schedule = merged
            return merged
        else:
            # Initialise le document par défaut dans Firestore
            doc_ref.set(_in_memory_schedule)
            return _in_memory_schedule.copy()
    except Exception as e:
        logger.error(f"Erreur lors de la lecture de la configuration Firestore: {e}")
        return _in_memory_schedule.copy()


def update_monitoring_schedule(data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Met à jour la configuration dynamique dans Firestore.
    """
    global _in_memory_schedule
    _in_memory_schedule.update(data)
    _in_memory_schedule["updated_at"] = datetime.now(timezone.utc).isoformat()

    db = get_firestore_client()
    if db is not None:
        try:
            doc_ref = db.collection(SETTINGS_COLLECTION).document(SCHEDULE_DOCUMENT)
            doc_ref.set(_in_memory_schedule, merge=True)
            logger.info("Configuration de surveillance mise à jour dans Firestore.")
        except Exception as e:
            logger.error(f"Erreur lors de la mise à jour de la configuration Firestore: {e}")

    return _in_memory_schedule.copy()


def update_last_check_timestamp(ts: Optional[float] = None) -> None:
    """Met à jour le timestamp de la dernière exécution."""
    global _in_memory_schedule
    now_ts = ts or datetime.now(timezone.utc).timestamp()
    _in_memory_schedule["last_check_timestamp"] = now_ts

    db = get_firestore_client()
    if db is not None:
        try:
            doc_ref = db.collection(SETTINGS_COLLECTION).document(SCHEDULE_DOCUMENT)
            doc_ref.set({"last_check_timestamp": now_ts}, merge=True)
        except Exception:
            pass

