export interface JourneyRoutePoint {
  pointId: string;
  pointName: string;
  pointType: string;
  sequence: number;
  coordinates: [number, number];
}

export interface JourneySuggestion {
  routeId: string;
  routeName: string;
  routeCoordinates: [number, number][];
  boardingPoint: JourneyRoutePoint;
  destinationPoint: JourneyRoutePoint;
  walkToBoardingKm: number;
  walkToDestinationKm: number;
  totalWalkingKm: number;
  walkToBoardingMinutes: number;
  walkToDestinationMinutes: number;
  totalWalkingMinutes: number;
  tier: string;
  rideDistanceKm: number;
  fareAmount: number;
  requiredCardBalance: number;
}

export interface JourneyPlanResponse {
  suggestions: JourneySuggestion[];
}

export interface JourneyPlanRequest {
  fromLat: number;
  fromLng: number;
  toLat: number;
  toLng: number;
}
