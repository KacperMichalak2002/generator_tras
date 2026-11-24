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
