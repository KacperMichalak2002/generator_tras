import type { RouteCollection } from "../types/route";

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

export function parseRouteCollection(json: unknown): RouteCollection {
  if (
    !isObject(json) ||
    json.type !== "FeatureCollection" ||
    !Array.isArray(json.features)
  ) {
    throw new Error("To nie jest GeoJSON typu FeatureCollection");
  }

  const features = json.features.filter(
    (f) =>
      isObject(f) &&
      isObject(f.geometry) &&
      f.geometry.type === "LineString" &&
      Array.isArray(f.geometry.coordinates),
  );
  if (features.length === 0) {
    throw new Error("W pliku nie ma żadnej trasy (LineString)");
  }

  return { type: "FeatureCollection", features } as RouteCollection;
}
