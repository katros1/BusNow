export type TripStatus = "ACTIVE" | "COMPLETED";

export interface TripSummaryDto {
  id: string;
  status: TripStatus;
  startedAt: string;
  endedAt: string | null;
  busId: string;
  plateNumber: string;
  model: string | null;
  routeId: string;
  routeName: string;
  routeCode: string | null;
  direction: string | null;
  passengersOnBoard: number;
  capacity: number | null;
  availableSeats: number | null;
  totalBoardings: number | null;
  totalAlightings: number | null;
}

export interface TripListParams {
  busId?: string;
  status?: TripStatus;
  page?: number;
  size?: number;
}

export interface PagedTrips {
  content: TripSummaryDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
