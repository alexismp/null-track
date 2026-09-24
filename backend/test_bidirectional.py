#!/usr/bin/env python3
"""
Tests unitaires pour la surveillance bidirectionnelle, le déclencheur retour,
le widget de surveillance ponctuelle et la détection des retards.
"""
from datetime import datetime, time, timedelta, timezone
import unittest

from monitor import (
    extract_cancelled_trains,
    extract_disrupted_trains,
    is_within_monitoring_window,
    TIMEZONE,
)

MOCK_BIDIRECTIONAL_SIRI = {
    "Siri": {
        "ServiceDelivery": {
            "ResponseTimestamp": "2026-09-24T17:30:00.000Z",
            "StopMonitoringDelivery": [
                {
                    "MonitoredStopVisit": [
                        # Train Aller vers Paris (annulé)
                        {
                            "ItemIdentifier": "VISIT-PARIS-001",
                            "MonitoredVehicleJourney": {
                                "LineRef": {"value": "STIF:Line::C01736:"},
                                "DirectionRef": {"value": "Retour"},
                                "DestinationName": [{"value": "Paris-Montparnasse"}],
                                "VehicleJourneyRef": {"value": "SNCF:TN:165101"},
                                "JourneyNote": [{"value": "POMA"}],
                                "MonitoredCall": {
                                    "StopPointName": [{"value": "Meudon"}],
                                    "AimedDepartureTime": "2026-09-24T08:15:00.000Z",
                                    "DepartureStatus": "cancelled",
                                },
                            },
                        },
                        # Train Aller vers Paris (retardé de 12 min)
                        {
                            "ItemIdentifier": "VISIT-PARIS-002",
                            "MonitoredVehicleJourney": {
                                "LineRef": {"value": "STIF:Line::C01736:"},
                                "DirectionRef": {"value": "Retour"},
                                "DestinationName": [{"value": "Paris-Montparnasse"}],
                                "VehicleJourneyRef": {"value": "SNCF:TN:165102"},
                                "JourneyNote": [{"value": "POGI"}],
                                "MonitoredCall": {
                                    "StopPointName": [{"value": "Meudon"}],
                                    "AimedDepartureTime": "2026-09-24T08:30:00.000Z",
                                    "ExpectedDepartureTime": "2026-09-24T08:42:00.000Z",
                                    "DepartureStatus": "delayed",
                                },
                            },
                        },
                        # Train Retour vers Rambouillet / Banlieue (annulé)
                        {
                            "ItemIdentifier": "VISIT-MEUDON-002",
                            "MonitoredVehicleJourney": {
                                "LineRef": {"value": "STIF:Line::C01736:"},
                                "DirectionRef": {"value": "Aller"},
                                "DestinationName": [{"value": "Rambouillet"}],
                                "VehicleJourneyRef": {"value": "SNCF:TN:165202"},
                                "JourneyNote": [{"value": "ROPO"}],
                                "MonitoredCall": {
                                    "StopPointName": [{"value": "Meudon"}],
                                    "AimedDepartureTime": "2026-09-24T18:15:00.000Z",
                                    "DepartureStatus": "cancelled",
                                },
                            },
                        },
                        # Train Retour vers Plaisir (retardé de 8 min)
                        {
                            "ItemIdentifier": "VISIT-MEUDON-003",
                            "MonitoredVehicleJourney": {
                                "LineRef": {"value": "STIF:Line::C01736:"},
                                "DirectionRef": {"value": "Aller"},
                                "DestinationName": [{"value": "Plaisir - Grignon"}],
                                "VehicleJourneyRef": {"value": "SNCF:TN:165203"},
                                "JourneyNote": [{"value": "POGI"}],
                                "MonitoredCall": {
                                    "StopPointName": [{"value": "Meudon"}],
                                    "AimedDepartureTime": "2026-09-24T18:30:00.000Z",
                                    "ExpectedDepartureTime": "2026-09-24T18:38:00.000Z",
                                    "DepartureStatus": "delayed",
                                },
                            },
                        },
                        # Train Retour à l'heure (aucun problème)
                        {
                            "ItemIdentifier": "VISIT-MEUDON-004",
                            "MonitoredVehicleJourney": {
                                "LineRef": {"value": "STIF:Line::C01736:"},
                                "DirectionRef": {"value": "Aller"},
                                "DestinationName": [{"value": "Mantes-la-Jolie"}],
                                "VehicleJourneyRef": {"value": "SNCF:TN:165204"},
                                "JourneyNote": [{"value": "MOPI"}],
                                "MonitoredCall": {
                                    "StopPointName": [{"value": "Meudon"}],
                                    "AimedDepartureTime": "2026-09-24T18:45:00.000Z",
                                    "DepartureStatus": "onTime",
                                },
                            },
                        },
                    ]
                }
            ]
        }
    }
}


class TestBidirectionalMonitoring(unittest.TestCase):

    def test_morning_window_detection(self):
        # Jeudi à 08h00 (plage matin 07h00 - 09h30)
        cfg = {
            "enabled": True,
            "active_days": [3],
            "morning_enabled": True,
            "morning_start_hour": 7, "morning_start_minute": 0,
            "morning_end_hour": 9, "morning_end_minute": 30,
            "evening_enabled": True,
            "evening_start_hour": 17, "evening_start_minute": 0,
            "evening_end_hour": 19, "evening_end_minute": 30,
        }
        test_now = datetime(2026, 9, 24, 8, 0, tzinfo=TIMEZONE)
        is_active, reason, active_dirs = is_within_monitoring_window(dt=test_now, schedule=cfg)
        self.assertTrue(is_active)
        self.assertEqual(active_dirs, ["TO_PARIS"])

    def test_evening_window_detection(self):
        # Jeudi à 18h00 (plage soir 17h00 - 19h30)
        cfg = {
            "enabled": True,
            "active_days": [3],
            "morning_enabled": True,
            "morning_start_hour": 7, "morning_start_minute": 0,
            "morning_end_hour": 9, "morning_end_minute": 30,
            "evening_enabled": True,
            "evening_start_hour": 17, "evening_start_minute": 0,
            "evening_end_hour": 19, "evening_end_minute": 30,
        }
        test_now = datetime(2026, 9, 24, 18, 0, tzinfo=TIMEZONE)
        is_active, reason, active_dirs = is_within_monitoring_window(dt=test_now, schedule=cfg)
        self.assertTrue(is_active)
        self.assertEqual(active_dirs, ["TO_MEUDON"])

    def test_outside_hours_without_trigger(self):
        # Jeudi à 14h00 (hors plages et aucun déclencheur)
        cfg = {
            "enabled": True,
            "active_days": [3],
            "morning_enabled": True, "morning_start_hour": 7, "morning_end_hour": 9, "morning_start_minute": 0, "morning_end_minute": 30,
            "evening_enabled": True, "evening_start_hour": 17, "evening_end_hour": 19, "evening_start_minute": 0, "evening_end_minute": 30,
        }
        test_now = datetime(2026, 9, 24, 14, 0, tzinfo=TIMEZONE)
        is_active, reason, active_dirs = is_within_monitoring_window(dt=test_now, schedule=cfg)
        self.assertFalse(is_active)
        self.assertEqual(active_dirs, [])

    def test_widget_quick_monitoring_to_meudon(self):
        # Utilisateur à Paris active le widget pour 1h (sens Paris ➔ Meudon)
        now_14h = datetime(2026, 9, 24, 14, 0, tzinfo=TIMEZONE)
        quick_until = (now_14h + timedelta(hours=1)).astimezone(timezone.utc).isoformat()
        cfg = {
            "enabled": True,
            "active_days": [0, 1, 2, 3, 4],
            "quick_monitoring_until": quick_until,
            "quick_monitoring_direction": "TO_MEUDON",
        }
        is_active, reason, active_dirs = is_within_monitoring_window(dt=now_14h, schedule=cfg)
        self.assertTrue(is_active)
        self.assertEqual(active_dirs, ["TO_MEUDON"])

    def test_widget_quick_monitoring_to_paris(self):
        # Utilisateur proche de Meudon active le widget pour 1h (sens Meudon ➔ Paris)
        now_14h = datetime(2026, 9, 24, 14, 0, tzinfo=TIMEZONE)
        quick_until = (now_14h + timedelta(hours=1)).astimezone(timezone.utc).isoformat()
        cfg = {
            "enabled": True,
            "active_days": [0, 1, 2, 3, 4],
            "quick_monitoring_until": quick_until,
            "quick_monitoring_direction": "TO_PARIS",
        }
        is_active, reason, active_dirs = is_within_monitoring_window(dt=now_14h, schedule=cfg)
        self.assertTrue(is_active)
        self.assertEqual(active_dirs, ["TO_PARIS"])

    def test_extract_disrupted_trains_delays_and_cancellations(self):
        # 1. Vérification sens TO_PARIS (1 annulé + 1 retardé de 12 min)
        disrupted_paris = extract_disrupted_trains(
            MOCK_BIDIRECTIONAL_SIRI,
            active_directions=["TO_PARIS"],
            include_delays=True,
            min_delay_minutes=5,
        )
        self.assertEqual(len(disrupted_paris), 2)
        codes = [t["status_code"] for t in disrupted_paris]
        self.assertIn("CANCELLED", codes)
        self.assertIn("DELAYED", codes)

        delayed_train = [t for t in disrupted_paris if t["status_code"] == "DELAYED"][0]
        self.assertEqual(delayed_train["delay_minutes"], 12)
        self.assertEqual(delayed_train["direction_code"], "TO_PARIS")

        # 2. Vérification sens TO_MEUDON (1 annulé + 1 retardé de 8 min)
        disrupted_meudon = extract_disrupted_trains(
            MOCK_BIDIRECTIONAL_SIRI,
            active_directions=["TO_MEUDON"],
            include_delays=True,
            min_delay_minutes=5,
        )
        self.assertEqual(len(disrupted_meudon), 2)
        meudon_delayed = [t for t in disrupted_meudon if t["status_code"] == "DELAYED"][0]
        self.assertEqual(meudon_delayed["delay_minutes"], 8)
        self.assertEqual(meudon_delayed["direction_code"], "TO_MEUDON")

        # 3. Sans inclusion des retards (seulement annulations)
        only_cancelled = extract_cancelled_trains(
            MOCK_BIDIRECTIONAL_SIRI,
            active_directions=["TO_PARIS", "TO_MEUDON"],
        )
        self.assertEqual(len(only_cancelled), 2)
        for t in only_cancelled:
            self.assertEqual(t["status_code"], "CANCELLED")


if __name__ == "__main__":
    unittest.main()
