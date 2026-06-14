# 🚌 BusNow AI Service

AI-powered bus stop recommendation service for Kigali commuters.

## Features

- Real-time stop recommendations
- Confidence scores
- Wait time estimates
- Fare information

## Quick Start

### 1. Install Dependencies

```bash
pip install -r requirements.txt
```

### 2. Run the Server

```bash
uvicorn app.main:app --reload
```

### 3. Open API Documentation

Go to: http://localhost:8000/docs

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | API info |
| GET | `/health` | Health check |
| GET | `/stops` | List all stops |
| GET | `/destinations` | List destinations |
| POST | `/predict` | Get recommendations |

## Example Request

```bash
curl -X POST "http://localhost:8000/predict" \
  -H "Content-Type: application/json" \
  -d '{
    "destination": "Kimironko",
    "user_latitude": -1.9350,
    "user_longitude": 30.0600,
    "hour": 8,
    "day_of_week": 1
  }'
```

## Example Response

```json
{
  "success": true,
  "destination": "Kimironko",
  "time_context": "peak",
  "best_stop": {
    "stop_name": "Kacyiru",
    "confidence": 87.5,
    "wait_time": 5,
    "fare": 210
  },
  "recommendations": [...]
}
```
