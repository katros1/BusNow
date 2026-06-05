export interface FareTier {
  tier: number;
  startKm: number;
  endKm: number | null;
  multiplier: number;
  exampleFare: number;
}

export interface FareSettings {
  basePriceFrw: number;
  updatedAt: string;
  tiers: FareTier[];
}

export interface UpdateFareSettingsPayload {
  basePriceFrw: number;
}
