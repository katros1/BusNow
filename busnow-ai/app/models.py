"""
BusNow AI Service - Pydantic Models
Request and Response schemas for the API
"""

from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime


# ============================================
# REQUEST MODELS
# ============================================

class RecommendationRequest(BaseModel):
    """
    Request for getting bus stop recommendations
    """
    destination: str = Field(
        ...,
        description="Where the commuter wants to go",
        example="Kimironko"
    )
    user_latitude: float = Field(
        ...,
        description="Commuter's current latitude",
        example=-1.9350
    )
    user_longitude: float = Field(
        ...,
        description="Commuter's current longitude",
        example=30.0600
    )
    hour: Optional[int] = Field(
        default=None,
        description="Current hour (0-23). If not provided, uses current time.",
        ge=0,
        le=23,
        example=8
    )
    day_of_week: Optional[int] = Field(
        default=None,
        description="Day of week (1=Monday, 7=Sunday). If not provided, uses current day.",
        ge=1,
        le=7,
        example=1
    )

    class Config:
        json_schema_extra = {
            "example": {
                "destination": "Kimironko",
                "user_latitude": -1.9350,
                "user_longitude": 30.0600,
                "hour": 8,
                "day_of_week": 1
            }
        }


class SingleStopRequest(BaseModel):
    """
    Request for checking a single stop recommendation
    """
    destination: str = Field(..., example="Kimironko")
    stop_name: str = Field(..., example="Kacyiru")
    hour: int = Field(..., ge=0, le=23, example=8)
    day_of_week: int = Field(..., ge=1, le=7, example=1)
    distance_to_stop: float = Field(..., ge=0, example=0.5)


# ============================================
# RESPONSE MODELS
# ============================================

class StopRecommendation(BaseModel):
    """
    A single stop recommendation
    """
    stop_name: str = Field(..., description="Name of the bus stop")
    latitude: float = Field(..., description="Stop latitude")
    longitude: float = Field(..., description="Stop longitude")
    confidence: float = Field(..., description="Confidence score (0-100)")
    recommended: bool = Field(..., description="Whether this stop is recommended")
    wait_time: int = Field(..., description="Estimated wait time in minutes")
    bus_frequency: int = Field(..., description="Buses per hour at this stop")
    fare: int = Field(..., description="Fare in RWF")
    distance_km: float = Field(..., description="Distance from user to stop in km")
    walking_time: int = Field(..., description="Estimated walking time in minutes")


class RecommendationResponse(BaseModel):
    """
    Response containing all stop recommendations
    """
    success: bool = Field(..., description="Whether the request was successful")
    destination: str = Field(..., description="Requested destination")
    timestamp: str = Field(..., description="Response timestamp")
    time_context: str = Field(..., description="Time context (peak/offpeak/weekend)")
    recommendations: List[StopRecommendation] = Field(
        ...,
        description="List of recommended stops, sorted by confidence"
    )
    best_stop: Optional[StopRecommendation] = Field(
        None,
        description="The highest recommended stop"
    )


class HealthResponse(BaseModel):
    """
    Health check response
    """
    status: str = Field(..., example="healthy")
    model_loaded: bool = Field(..., example=True)
    version: str = Field(..., example="1.0.0")
    timestamp: str = Field(...)


class StopsResponse(BaseModel):
    """
    Response with all available stops
    """
    success: bool = True
    total_stops: int
    stops: List[dict]


class ErrorResponse(BaseModel):
    """
    Error response
    """
    success: bool = False
    error: str
    detail: Optional[str] = None
    