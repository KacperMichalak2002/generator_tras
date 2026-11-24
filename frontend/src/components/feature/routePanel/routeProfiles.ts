import {
  faBicycle,
  faMotorcycle,
  type IconDefinition,
} from "@fortawesome/free-solid-svg-icons";
import type { SubprofileId, VehicleType } from "../../../types/route";

export type Subprofile = {
  id: SubprofileId;
  label: string;
  minKm: number;
  maxKm: number;
};

export const PROFILES: Record<
  VehicleType,
  { label: string; icon: IconDefinition; subprofiles: Subprofile[] }
> = {
  bike: {
    label: "Rower",
    icon: faBicycle,
    subprofiles: [
      { id: "bike_road", label: "Szosa", minKm: 5, maxKm: 200 },
      { id: "gravel", label: "Gravel", minKm: 5, maxKm: 200 },
      { id: "mtb", label: "MTB", minKm: 5, maxKm: 200 },
    ],
  },
  motorcycle: {
    label: "Motocykl",
    icon: faMotorcycle,
    subprofiles: [
      { id: "moto_road", label: "Szosa", minKm: 20, maxKm: 500 },
      { id: "moto_offroad", label: "Teren", minKm: 20, maxKm: 150 },
    ],
  },
};

export const VEHICLES = Object.keys(PROFILES) as VehicleType[];

export function findSubprofile(id: SubprofileId): Subprofile {
  for (const vehicle of VEHICLES) {
    const found = PROFILES[vehicle].subprofiles.find((s) => s.id === id);
    if (found) return found;
  }
  throw new Error(`Nieznany podprofil: ${id}`);
}

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
