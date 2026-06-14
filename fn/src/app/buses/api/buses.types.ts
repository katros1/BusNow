export interface Bus {
  id: string;
  plateNumber: string;
  gpsImei: string;
  model: string;
  capacity: number;
  currentLatitude?: number;
  currentLongitude?: number;
  currentDriver?: {
    id: string;
    fullName: string;
  };
  routeCode?: {
    id: string;
    code: string;
  };
  createdAt: string;
  updatedAt: string;
}

export interface CreateBusPayload {
  plateNumber: string;
  gpsImei: string;
  model: string;
  capacity: number;
  driverId: string;
  routeCodeId: string;
}

export type UpdateBusPayload = Partial<CreateBusPayload>;
