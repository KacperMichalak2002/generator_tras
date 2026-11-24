import type { RouteCollection, RouteParams } from "../types/route";
import { parseRouteCollection } from "../utils/parseRouteCollections";
import { pickFile } from "../utils/pickFile";

export async function generateRoutes(
  params: RouteParams,
): Promise<RouteCollection | null> {
  console.log("[route] parametry (na razie nieużywane):", params);

  const file = await pickFile(".geojson,.json,application/geo+json");
  if (!file) return null;

  let json: unknown;
  try {
    json = JSON.parse(await file.text());
  } catch {
    throw new Error(`Plik ${file.name} nie jest poprawnym JSON-em`);
  }
  return parseRouteCollection(json);
}
