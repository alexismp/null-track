import os
from datetime import time
from zoneinfo import ZoneInfo
try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    # Lecture basique de .env si python-dotenv n'est pas installé
    if os.path.exists(".env"):
        with open(".env") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    os.environ.setdefault(k.strip(), v.strip())

# --- PRIM IDFM Configuration ---
# Clé API PRIM (à obtenir gratuitement sur https://prim.iledefrance-mobilites.fr/)
PRIM_API_KEY = os.getenv("PRIM_API_KEY", "")
PRIM_API_URL = os.getenv(
    "PRIM_API_URL",
    "https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring"
)

# Gare de Meudon (Code StopArea IDFM)
# STIF:StopArea:SP:43162: correspond à la gare de Meudon (Ligne N)
DEFAULT_MONITORING_REF = "STIF:StopArea:SP:43162:"
MONITORING_REF = os.getenv("PRIM_MONITORING_REF", DEFAULT_MONITORING_REF)

# Ligne N (Code IDFM STIF:Line::C01736:)
LINE_REF = os.getenv("PRIM_LINE_REF", "STIF:Line::C01736:")

# Filtre de destination : on cherche les trains à destination de Paris-Montparnasse
DESTINATION_FILTER = os.getenv("DESTINATION_FILTER", "Montparnasse")

# --- Horaires et Jours de surveillance ---
# Jours actifs : 0=Lundi, 1=Mardi, 2=Mercredi, 3=Jeudi, 4=Vendredi, 5=Samedi, 6=Dimanche
ACTIVE_DAYS = [int(d.strip()) for d in os.getenv("ACTIVE_DAYS", "0,1,2,3,4").split(",")]

# Plage horaire par défaut : 07h00 à 09h30
TIMEZONE = ZoneInfo(os.getenv("TIMEZONE", "Europe/Paris"))
START_HOUR = int(os.getenv("START_HOUR", "7"))
START_MINUTE = int(os.getenv("START_MINUTE", "0"))
END_HOUR = int(os.getenv("END_HOUR", "9"))
END_MINUTE = int(os.getenv("END_MINUTE", "30"))

WINDOW_START = time(START_HOUR, START_MINUTE)
WINDOW_END = time(END_HOUR, END_MINUTE)

# Option pour forcer l'exécution hors plage horaire (utile pour les tests et démos)
FORCE_CHECK = os.getenv("FORCE_CHECK", "false").lower() in ("true", "1", "yes")

# Fréquence par défaut : 3 minutes
DEFAULT_FREQUENCY_MINUTES = int(os.getenv("DEFAULT_FREQUENCY_MINUTES", "3"))

# --- Firebase & Firestore Configuration ---
FCM_TOPIC = os.getenv("FCM_TOPIC", "trains_meudon_montparnasse")
FIRESTORE_COLLECTION = os.getenv("FIRESTORE_COLLECTION", "notified_trains")
SETTINGS_COLLECTION = os.getenv("SETTINGS_COLLECTION", "settings")
SCHEDULE_DOCUMENT = os.getenv("SCHEDULE_DOCUMENT", "monitoring_schedule")
FIREBASE_PROJECT_ID = os.getenv("FIREBASE_PROJECT_ID", "")
