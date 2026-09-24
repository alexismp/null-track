import logging
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Optional

from config import FIRESTORE_COLLECTION, FIREBASE_PROJECT_ID

logger = logging.getLogger("null-track.firestore")

_db = None
_in_memory_cache = set()


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
