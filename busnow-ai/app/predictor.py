"""
BusNow AI Service - Predictor
Handles loading model and making predictions
"""

import joblib
import numpy as np
from typing import List, Dict, Tuple, Optional
from datetime import datetime

from app.config import MODEL_PATH, ENCODERS_PATH, STOPS_DATA, ALL_STOPS


class BusNowPredictor:
    """
    Main predictor class for BusNow AI recommendations
    """
    
    def __init__(self):
        """Initialize the predictor by loading the model and encoders"""
        self.model = None
        self.encoders = None
        self.is_loaded = False
        self._load_model()
    
    def _load_model(self):
        """Load the trained model and encoders"""
        try:
            print("🔄 Loading BusNow AI model...")
            self.model = joblib.load(MODEL_PATH)
            self.encoders = joblib.load(ENCODERS_PATH)
            self.is_loaded = True
            print("✅ Model loaded successfully!")
        except Exception as e:
            print(f"❌ Error loading model: {e}")
            self.is_loaded = False
    
    def _is_peak_hour(self, hour: int) -> bool:
        """Check if it's rush hour"""
        return (7 <= hour <= 9) or (17 <= hour <= 19)
    
    def _is_weekend(self, day_of_week: int) -> bool:
        """Check if it's weekend"""
        return day_of_week >= 6
    
    def _get_time_period(self, hour: int, day_of_week: int) -> str:
        """Get human-readable time period"""
        if self._is_weekend(day_of_week):
            return "weekend"
        elif self._is_peak_hour(hour):
            return "peak"
        else:
            return "offpeak"
    
    def _calculate_distance(self, lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        """Calculate distance between two points in kilometers"""
        lat_diff = abs(lat2 - lat1) * 111
        lon_diff = abs(lon2 - lon1) * 111
        distance = np.sqrt(lat_diff**2 + lon_diff**2)
        return round(distance, 2)
    
    def _get_bus_frequency(self, stop_name: str, hour: int, day_of_week: int) -> int:
        """Get bus frequency for a stop based on time"""
        stop = STOPS_DATA[stop_name]
        
        if self._is_weekend(day_of_week):
            return stop['weekend_frequency']
        elif self._is_peak_hour(hour):
            return stop['peak_frequency']
        else:
            return stop['offpeak_frequency']
    
    def _get_wait_time(self, stop_name: str, hour: int, day_of_week: int) -> int:
        """Get average wait time for a stop based on time"""
        stop = STOPS_DATA[stop_name]
        
        if self._is_weekend(day_of_week):
            return stop['weekend_wait']
        elif self._is_peak_hour(hour):
            return stop['peak_wait']
        else:
            return stop['offpeak_wait']
    
    def _get_fare(self, stop_name: str, destination: str) -> int:
        """Get fare from stop to destination"""
        # For simplicity, using fare_to_kimironko
        # In production, calculate based on actual route
        return STOPS_DATA[stop_name].get('fare_to_kimironko', 200)
    
    def _calculate_walking_time(self, distance_km: float) -> int:
        """Calculate walking time in minutes (assuming 5 km/h walking speed)"""
        return int(distance_km * 12)  # 12 minutes per km
    
    def predict_single_stop(
        self,
        destination: str,
        stop_name: str,
        hour: int,
        day_of_week: int,
        distance_to_stop: float
    ) -> Tuple[int, float]:
        """
        Predict recommendation for a single stop
        
        Returns:
            Tuple of (recommended: 0 or 1, confidence: 0-100)
        """
        if not self.is_loaded:
            raise RuntimeError("Model not loaded")
        
        # Get values
        is_peak = 1 if self._is_peak_hour(hour) else 0
        is_wknd = 1 if self._is_weekend(day_of_week) else 0
        bus_frequency = self._get_bus_frequency(stop_name, hour, day_of_week)
        avg_wait_time = self._get_wait_time(stop_name, hour, day_of_week)
        fare = self._get_fare(stop_name, destination)
        
        # Encode categorical variables
        dest_encoder = self.encoders['destination_encoder']
        stop_encoder = self.encoders['stop_encoder']

        # If the destination wasn't in the original training set, map it to
        # the nearest known class by stop_order so the model still runs.
        known_dests = list(dest_encoder.classes_)
        if destination not in known_dests:
            stop_info = STOPS_DATA.get(destination, {})
            order = stop_info.get('stop_order', 1)
            # Pick known destination closest in stop_order
            def _closest(d):
                return abs(STOPS_DATA.get(d, {}).get('stop_order', 1) - order)
            destination = min(known_dests, key=_closest)
        dest_encoded = dest_encoder.transform([destination])[0]

        known_stops = list(stop_encoder.classes_)
        proxy_stop = stop_name if stop_name in known_stops else known_stops[0]
        stop_encoded = stop_encoder.transform([proxy_stop])[0]
        
        # Create feature array (must match training order!)
        features = [[
            hour,
            day_of_week,
            is_peak,
            is_wknd,
            bus_frequency,
            avg_wait_time,
            distance_to_stop,
            fare,
            dest_encoded,
            stop_encoded
        ]]
        
        # Predict
        prediction = self.model.predict(features)[0]
        probabilities = self.model.predict_proba(features)[0]
        confidence = round(max(probabilities) * 100, 1)
        
        return int(prediction), confidence
    
    def get_recommendations(
        self,
        destination: str,
        user_latitude: float,
        user_longitude: float,
        hour: Optional[int] = None,
        day_of_week: Optional[int] = None
    ) -> Dict:
        """
        Get recommendations for all stops
        
        Returns:
            Dictionary with recommendations sorted by confidence
        """
        if not self.is_loaded:
            raise RuntimeError("Model not loaded")
        
        # Use current time if not provided
        now = datetime.now()
        if hour is None:
            hour = now.hour
        if day_of_week is None:
            day_of_week = now.isoweekday()  # 1=Monday, 7=Sunday
        
        # Validate destination
        if destination not in ALL_STOPS:
            raise ValueError(f"Invalid destination: {destination}. Must be one of {ALL_STOPS}")
        
        # Get recommendations for all stops (except destination)
        recommendations = []
        
        for stop_name in ALL_STOPS:
            if stop_name == destination:
                continue  # Skip destination itself
            
            stop_info = STOPS_DATA[stop_name]
            
            # Calculate distance from user to this stop
            distance_km = self._calculate_distance(
                user_latitude, user_longitude,
                stop_info['latitude'], stop_info['longitude']
            )
            
            # Get prediction
            recommended, confidence = self.predict_single_stop(
                destination=destination,
                stop_name=stop_name,
                hour=hour,
                day_of_week=day_of_week,
                distance_to_stop=distance_km
            )
            
            # Get additional info
            bus_frequency = self._get_bus_frequency(stop_name, hour, day_of_week)
            wait_time = self._get_wait_time(stop_name, hour, day_of_week)
            fare = self._get_fare(stop_name, destination)
            walking_time = self._calculate_walking_time(distance_km)
            
            recommendations.append({
                'stop_name': stop_name,
                'latitude': stop_info['latitude'],
                'longitude': stop_info['longitude'],
                'confidence': confidence,
                'recommended': recommended == 1,
                'wait_time': wait_time,
                'bus_frequency': bus_frequency,
                'fare': fare,
                'distance_km': distance_km,
                'walking_time': walking_time
            })
        
        # Sort by confidence (highest first)
        recommendations.sort(key=lambda x: x['confidence'], reverse=True)
        
        # Find best stop (highest confidence that is recommended)
        best_stop = None
        for rec in recommendations:
            if rec['recommended']:
                best_stop = rec
                break
        
        return {
            'success': True,
            'destination': destination,
            'timestamp': now.isoformat(),
            'time_context': self._get_time_period(hour, day_of_week),
            'recommendations': recommendations,
            'best_stop': best_stop
        }


# Create global predictor instance
predictor = BusNowPredictor()
