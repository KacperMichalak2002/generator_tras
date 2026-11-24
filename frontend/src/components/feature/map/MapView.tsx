import {
  LngLatBounds,
  MapLibreMap,
  NavigationControl,
  setWorkerUrl,
  type GeoJSONSource,
} from "maplibre-gl";
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";
import { useEffect, useRef, useState } from "react";
import { osmStyle } from "./osmStyle";
import { DEFAULT_CENTER, DEFAULT_ZOOM } from "../../../config/map";
import "maplibre-gl/dist/maplibre-gl.css";
import { useRouteStore } from "../../../stores/routeStore";
import type { RouteCollection } from "../../../types/route";

setWorkerUrl(workerUrl);

const ROUTES_SOURCE = "routes";
const EMPTY: RouteCollection = { type: "FeatureCollection", features: [] };

function boundsOf(routes: RouteCollection): LngLatBounds | null {
  const bounds = new LngLatBounds();
  for (const feature of routes.features) {
    for (const [lng, lat] of feature.geometry.coordinates) {
      bounds.extend([lng, lat]);
    }
  }
  return bounds.isEmpty() ? null : bounds;
}

function MapView() {
  const routes = useRouteStore((s) => s.routes);
  const containerRef = useRef<HTMLDivElement>(null);
  const [map, setMap] = useState<MapLibreMap | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const instance = new MapLibreMap({
      container: containerRef.current,
      style: osmStyle,
      center: DEFAULT_CENTER,
      zoom: DEFAULT_ZOOM,
    });
    instance.addControl(new NavigationControl(), "top-right");

    instance.on("load", () => {
      instance.addSource(ROUTES_SOURCE, { type: "geojson", data: EMPTY });
      instance.addLayer({
        id: "routes-line",
        type: "line",
        source: ROUTES_SOURCE,
        layout: { "line-join": "round", "line-cap": "round" },
        paint: {
          "line-color": ["coalesce", ["get", "stroke"], "#1a73e8"],
          "line-width": ["coalesce", ["get", "stroke-width"], 4],
          "line-opacity": 0.85,
        },
      });
      setMap(instance);
    });

    return () => instance.remove();
  }, []);

  useEffect(() => {
    if (!map) return;
    map.getSource<GeoJSONSource>(ROUTES_SOURCE)?.setData(routes ?? EMPTY);

    const bounds = routes && boundsOf(routes);
    if (bounds) {
      const left = window.innerWidth >= 768 ? 420 : 40;
      map.fitBounds(bounds, {
        padding: { top: 60, bottom: 60, right: 60, left },
      });
    }
  }, [map, routes]);

  return <div ref={containerRef} className="min-h-0 flex-1" />;
}

export default MapView;
