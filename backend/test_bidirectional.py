#!/usr/bin/env python3
"""
Tests unitaires pour la surveillance bidirectionnelle et le déclencheur retour.
"""
from datetime import datetime, time, timedelta, timezone
import unittest
from unittest.mock import patch

from monitor import extract_cancelled_trains, is_within_monitoring_window, TIMEZONE

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
                        # Train Retour à l'heure (non annulé)
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
            "active_days": [3], # Jeudi
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
            "active_days": [3], # Jeudi
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

    def test_on_demand_return_commute_trigger(self):
        # L'utilisateur déclenche à 15h30 (hors horaires habituels) un retour de 2h (jusqu'à 17h30)
        now_15h30 = datetime(2026, 9, 24, 15, 30, tzinfo=TIMEZONE)
        ret_until = (now_15h30 + timedelta(hours=2)).astimezone(timezone.utc).isoformat()

        cfg = {
            "enabled": True,
            "active_days": [0, 1, 2, 3, 4],
            "morning_enabled": True, "morning_start_hour": 7, "morning_end_hour": 9, "morning_start_minute": 0, "morning_end_minute": 30,
            "evening_enabled": True, "evening_start_hour": 17, "evening_end_hour": 19, "evening_start_minute": 0, "evening_end_minute": 30,
            "return_commute_until": ret_until,
        }

        is_active, reason, active_dirs = is_within_monitoring_window(dt=now_15h30, schedule=cfg)
        self.assertTrue(is_active)
        self.assertIn("TO_MEUDON", active_dirs)

    def test_direction_filtering_cancelled_trains(self):
        # 1. Vérification avec seulement direction TO_PARIS
        cancelled_paris = extract_cancelled_trains(MOCK_BIDIRECTIONAL_SIRI, active_directions=["TO_PARIS"])
        self.assertEqual(len(cancelled_paris), 1)
        self.assertEqual(cancelled_paris[0]["direction_code"], "TO_PARIS")
        self.assertEqual(cancelled_paris[0]["mission_code"], "POMA")

        # 2. Vérification avec seulement direction TO_MEUDON
        cancelled_meudon = extract_cancelled_trains(MOCK_BIDIRECTIONAL_SIRI, active_directions=["TO_MEUDON"])
        self.assertEqual(len(cancelled_meudon), 1)
        self.assertEqual(cancelled_meudon[0]["direction_code"], "TO_MEUDON")
        self.assertEqual(cancelled_meudon[0]["mission_code"], "ROPO")

        # 3. Vérification avec les deux directions actives
        cancelled_both = extract_cancelled_trains(MOCK_BIDIRECTIONAL_SIRI, active_directions=["TO_PARIS", "TO_MEUDON"])
        self.assertEqual(len(cancelled_both), 2)


if __name__ == "__main__":
    unittest.main()
