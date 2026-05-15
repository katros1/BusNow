/** Flat snapshot published by the new Redis/STOMP pipeline. */
export interface VehicleLiveSnapshot {
  busId: string;
  plateNumber: string;
  routeId: string | null;
  routeCode: string | null;
  routeName: string | null;
  latitude: number | null;
  longitude: number | null;
  speedKmh: number | null;
  headingDeg: number | null;
  gpsValid: boolean;
  gpsStale: boolean;
  currentStopName: string | null;
  nextStopName: string | null;
  passengersOnBoard: number;
  availableSeats: number | null;
  tripId: string | null;
  timestamp: string;
}

/** @deprecated legacy nested shape — kept for reference only */
export interface VehiclePositionEvent {
  busId: string;
  plateNumber: string;
  deviceId: string;
  gpsValid: boolean;
  latitude: number | null;
  longitude: number | null;
  speedKmh: number | null;
  headingDeg: number | null;
  timestamp: string;
  route: VehicleRouteInfo | null;
  trip: VehicleTripInfo | null;
  currentStop: VehicleStopInfo | null;
}

export interface VehicleRouteInfo {
  id: string | null;
  name: string | null;
  code: string | null;
  direction: string | null;
}

export interface VehicleTripInfo {
  id: string;
  status: "ACTIVE" | "COMPLETED";
  startedAt: string;
  passengersIn: number;
  passengersOut: number;
  onBoard: number;
  availableSeats: number | null;
}

export interface VehicleStopInfo {
  id: string;
  name: string;
  sequence: number;
}

export interface TrackingVehicleDto {
  busId: string;
  plateNumber: string;
  model: string | null;
  capacity: number | null;
  latitude: number | null;
  longitude: number | null;
  routeId: string | null;
  routeName: string | null;
  routeCode: string | null;
  direction: string | null;
  activeTripId: string | null;
  tripStartedAt: string | null;
  passengersOnBoard: number | null;
  availableSeats: number | null;
}

export interface RouteDetailDto {
  id: string;
  name: string;
  routePath: [number, number][];
  startBusPark: BusParkShapeDto;
  endBusPark: BusParkShapeDto;
  stops: StopShapeDto[];
}

export interface BusParkShapeDto {
  id: string;
  name: string;
  coordinates: [number, number][];
}

export interface StopShapeDto {
  id: string;
  name: string;
  sequence: number;
  coordinates: [number, number][];
}
