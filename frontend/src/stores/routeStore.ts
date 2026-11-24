import { create } from "zustand";
import type { RouteCollection, RouteParams } from "../types/route";

const DEFAULT_PARAMS: RouteParams = {
  vehicle: "bike",
  subprofile: "bike_road",
  distanceKm: 50,
  curviness: 50,
  avoidMainRoads: true,
};

type RouteState = {
  params: RouteParams;
  start: [number, number] | null;
  routes: RouteCollection | null;
  selectedVariant: number | null;

  updateParams: (patch: Partial<RouteParams>) => void;
  setStart: (start: [number, number] | null) => void;
  setRoutes: (routes: RouteCollection) => void;
  selectVariant: (variant: number | null) => void;
};

export const useRouteStore = create<RouteState>()((set) => ({
  params: DEFAULT_PARAMS,
  start: null,
  routes: null,
  selectedVariant: null,

  updateParams: (patch) => set((s) => ({ params: { ...s.params, ...patch } })),
  setStart: (start) => set({ start }),
  setRoutes: (routes) => set({ routes, selectedVariant: null }),
  selectVariant: (selectedVariant) => set({ selectedVariant }),
}));
