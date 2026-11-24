import type { FeatureCollection, LineString } from "geojson";
export type VehicleType = "bike" | "motorcycle";

export type SubprofileId =
  | "bike_road"
  | "gravel"
  | "mtb"
  | "moto_road"
  | "moto_offroad";

export type RouteParams = {
  vehicle: VehicleType;
  subprofile: SubprofileId;
  distanceKm: number;
  curviness: number;
  avoidMainRoads: boolean;
};

export type RouteProperties = {
  variant?: number;
  lengthKm?: number;
  stroke?: string;
  "stroke-width"?: number;
};

export type RouteCollection = FeatureCollection<LineString, RouteProperties>;
