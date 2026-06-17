"""
BusNow AI Service Configuration
"""

import os

# Paths
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ML_MODELS_DIR = os.path.join(BASE_DIR, "ml_models")

# Model files
MODEL_PATH = os.path.join(ML_MODELS_DIR, "busnow_model.pkl")
ENCODERS_PATH = os.path.join(ML_MODELS_DIR, "busnow_encoders.pkl")

# API Settings
API_TITLE = "BusNow AI Service"
API_DESCRIPTION = """
🚌 **BusNow AI-Powered Stop Recommendation Service**

This API provides intelligent bus stop recommendations for commuters in Kigali.

## Features
- Real-time stop recommendations based on multiple factors
- Confidence scores for each recommendation
- Wait time and fare estimates

## How It Works
The AI model analyzes:
- Time of day and day of week
- Bus frequency at each stop
- Average wait times
- Distance from commuter to stop

And recommends the optimal bus stop with a confidence score.
"""
API_VERSION = "1.0.0"

# Bus stops data (same as training)
STOPS_DATA = {
    'Downtown': {
        'latitude': -1.9500,
        'longitude': 30.0605,
        'stop_order': 1,
        'peak_frequency': 14,
        'offpeak_frequency': 8,
        'weekend_frequency': 6,
        'peak_wait': 4,
        'offpeak_wait': 8,
        'weekend_wait': 10,
        'fare_to_kimironko': 500
    },
    'Kimicanga': {
        'latitude': -1.9490,
        'longitude': 30.0590,
        'stop_order': 2,
        'peak_frequency': 13,
        'offpeak_frequency': 7,
        'weekend_frequency': 5,
        'peak_wait': 5,
        'offpeak_wait': 9,
        'weekend_wait': 12,
        'fare_to_kimironko': 470
    },
    'La Colombiere': {
        'latitude': -1.9470,
        'longitude': 30.0575,
        'stop_order': 3,
        'peak_frequency': 12,
        'offpeak_frequency': 7,
        'weekend_frequency': 5,
        'peak_wait': 5,
        'offpeak_wait': 9,
        'weekend_wait': 12,
        'fare_to_kimironko': 440
    },
    'RIB': {
        'latitude': -1.9370,
        'longitude': 30.0595,
        'stop_order': 4,
        'peak_frequency': 11,
        'offpeak_frequency': 6,
        'weekend_frequency': 4,
        'peak_wait': 6,
        'offpeak_wait': 10,
        'weekend_wait': 14,
        'fare_to_kimironko': 410
    },
    'MINIJUST': {
        'latitude': -1.9340,
        'longitude': 30.0610,
        'stop_order': 5,
        'peak_frequency': 11,
        'offpeak_frequency': 6,
        'weekend_frequency': 4,
        'peak_wait': 6,
        'offpeak_wait': 10,
        'weekend_wait': 14,
        'fare_to_kimironko': 380
    },
    'Gishushu': {
        'latitude': -1.9430,
        'longitude': 30.0640,
        'stop_order': 6,
        'peak_frequency': 10,
        'offpeak_frequency': 6,
        'weekend_frequency': 4,
        'peak_wait': 6,
        'offpeak_wait': 10,
        'weekend_wait': 15,
        'fare_to_kimironko': 350
    },
    'Kigali Heights': {
        'latitude': -1.9456,
        'longitude': 30.0612,
        'stop_order': 7,
        'peak_frequency': 12,
        'offpeak_frequency': 7,
        'weekend_frequency': 5,
        'peak_wait': 5,
        'offpeak_wait': 9,
        'weekend_wait': 13,
        'fare_to_kimironko': 320
    },
    'Beausejour': {
        'latitude': -1.9465,
        'longitude': 30.0710,
        'stop_order': 8,
        'peak_frequency': 10,
        'offpeak_frequency': 6,
        'weekend_frequency': 4,
        'peak_wait': 6,
        'offpeak_wait': 10,
        'weekend_wait': 15,
        'fare_to_kimironko': 290
    },
    'Kisimenti': {
        'latitude': -1.9478,
        'longitude': 30.0789,
        'stop_order': 9,
        'peak_frequency': 11,
        'offpeak_frequency': 6,
        'weekend_frequency': 5,
        'peak_wait': 5,
        'offpeak_wait': 10,
        'weekend_wait': 14,
        'fare_to_kimironko': 260
    },
    'Stadium': {
        'latitude': -1.9500,
        'longitude': 30.0860,
        'stop_order': 10,
        'peak_frequency': 10,
        'offpeak_frequency': 6,
        'weekend_frequency': 5,
        'peak_wait': 6,
        'offpeak_wait': 10,
        'weekend_wait': 13,
        'fare_to_kimironko': 230
    },
    'Remera': {
        'latitude': -1.9562,
        'longitude': 30.0987,
        'stop_order': 11,
        'peak_frequency': 12,
        'offpeak_frequency': 7,
        'weekend_frequency': 5,
        'peak_wait': 5,
        'offpeak_wait': 9,
        'weekend_wait': 12,
        'fare_to_kimironko': 180
    },
    'Sonatubes': {
        'latitude': -1.9550,
        'longitude': 30.1020,
        'stop_order': 12,
        'peak_frequency': 10,
        'offpeak_frequency': 6,
        'weekend_frequency': 4,
        'peak_wait': 6,
        'offpeak_wait': 10,
        'weekend_wait': 14,
        'fare_to_kimironko': 150
    },
    'Chez Lando': {
        'latitude': -1.9534,
        'longitude': 30.1045,
        'stop_order': 13,
        'peak_frequency': 9,
        'offpeak_frequency': 5,
        'weekend_frequency': 4,
        'peak_wait': 7,
        'offpeak_wait': 12,
        'weekend_wait': 16,
        'fare_to_kimironko': 120
    },
    'Kwa Rwahama': {
        'latitude': -1.9540,
        'longitude': 30.1065,
        'stop_order': 14,
        'peak_frequency': 9,
        'offpeak_frequency': 5,
        'weekend_frequency': 4,
        'peak_wait': 7,
        'offpeak_wait': 12,
        'weekend_wait': 16,
        'fare_to_kimironko': 100
    },
    'Kicukiro Centre': {
        'latitude': -1.9680,
        'longitude': 30.1100,
        'stop_order': 15,
        'peak_frequency': 10,
        'offpeak_frequency': 6,
        'weekend_frequency': 4,
        'peak_wait': 6,
        'offpeak_wait': 11,
        'weekend_wait': 15,
        'fare_to_kimironko': 60
    },
    'Kimironko': {
        'latitude': -1.9456,
        'longitude': 30.1123,
        'stop_order': 16,
        'peak_frequency': 13,
        'offpeak_frequency': 7,
        'weekend_frequency': 6,
        'peak_wait': 4,
        'offpeak_wait': 8,
        'weekend_wait': 11,
        'fare_to_kimironko': 0
    }
}

ALL_STOPS = list(STOPS_DATA.keys())
