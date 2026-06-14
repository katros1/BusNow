"""
BusNow AI Service - Main FastAPI Application
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from datetime import datetime

from app.config import API_TITLE, API_DESCRIPTION, API_VERSION, ALL_STOPS, STOPS_DATA
from app.models import (
    RecommendationRequest,
    RecommendationResponse,
    SingleStopRequest,
    StopRecommendation,
    HealthResponse,
    StopsResponse,
    ErrorResponse
)
from app.predictor import predictor


# ============================================
# CREATE FASTAPI APP
# ============================================

app = FastAPI(
    title=API_TITLE,
    description=API_DESCRIPTION,
    version=API_VERSION,
    docs_url="/docs",
    redoc_url="/redoc"
)

# Add CORS middleware (allows Flutter/Spring Boot to call this API)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, specify exact origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ============================================
# ENDPOINTS
# ============================================

@app.get("/", tags=["Info"])
async def root():
    """
    Root endpoint - API information
    """
    return {
        "service": "BusNow AI Service",
        "version": API_VERSION,
        "status": "running",
        "docs": "/docs",
        "health": "/health"
    }


@app.get("/health", response_model=HealthResponse, tags=["Info"])
async def health_check():
    """
    Health check endpoint
    """
    return HealthResponse(
        status="healthy" if predictor.is_loaded else "unhealthy",
        model_loaded=predictor.is_loaded,
        version=API_VERSION,
        timestamp=datetime.now().isoformat()
    )


@app.get("/stops", response_model=StopsResponse, tags=["Stops"])
async def get_all_stops():
    """
    Get all available bus stops
    """
    stops = []
    for name, info in STOPS_DATA.items():
        stops.append({
            "name": name,
            "latitude": info["latitude"],
            "longitude": info["longitude"],
            "stop_order": info["stop_order"]
        })
    
    # Sort by stop order
    stops.sort(key=lambda x: x["stop_order"])
    
    return StopsResponse(
        success=True,
        total_stops=len(stops),
        stops=stops
    )


@app.get("/destinations", tags=["Stops"])
async def get_destinations():
    """
    Get list of available destinations
    """
    return {
        "success": True,
        "destinations": ALL_STOPS
    }


@app.post("/predict", response_model=RecommendationResponse, tags=["Predictions"])
async def get_recommendations(request: RecommendationRequest):
    """
    🎯 **Main Prediction Endpoint**
    
    Get AI-powered bus stop recommendations based on:
    - Destination
    - User's current location
    - Time of day
    - Day of week
    
    Returns a list of stops sorted by recommendation confidence.
    """
    try:
        # Validate destination
        if request.destination not in ALL_STOPS:
            raise HTTPException(
                status_code=400,
                detail=f"Invalid destination: {request.destination}. Available: {ALL_STOPS}"
            )
        
        # Get recommendations
        result = predictor.get_recommendations(
            destination=request.destination,
            user_latitude=request.user_latitude,
            user_longitude=request.user_longitude,
            hour=request.hour,
            day_of_week=request.day_of_week
        )
        
        # Convert to response model
        recommendations = [
            StopRecommendation(**rec) for rec in result['recommendations']
        ]
        
        best_stop = None
        if result['best_stop']:
            best_stop = StopRecommendation(**result['best_stop'])
        
        return RecommendationResponse(
            success=True,
            destination=result['destination'],
            timestamp=result['timestamp'],
            time_context=result['time_context'],
            recommendations=recommendations,
            best_stop=best_stop
        )
    
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Internal error: {str(e)}")


@app.post("/predict/single", tags=["Predictions"])
async def predict_single_stop(request: SingleStopRequest):
    """
    Get recommendation for a single specific stop
    """
    try:
        # Validate
        if request.destination not in ALL_STOPS:
            raise HTTPException(status_code=400, detail=f"Invalid destination: {request.destination}")
        if request.stop_name not in ALL_STOPS:
            raise HTTPException(status_code=400, detail=f"Invalid stop: {request.stop_name}")
        
        # Predict
        recommended, confidence = predictor.predict_single_stop(
            destination=request.destination,
            stop_name=request.stop_name,
            hour=request.hour,
            day_of_week=request.day_of_week,
            distance_to_stop=request.distance_to_stop
        )
        
        return {
            "success": True,
            "stop_name": request.stop_name,
            "destination": request.destination,
            "recommended": recommended == 1,
            "confidence": confidence
        }
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ============================================
# RUN SERVER (for local development)
# ============================================

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
    