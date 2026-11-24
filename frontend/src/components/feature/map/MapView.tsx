import { MapLibreMap, NavigationControl } from "maplibre-gl";
import { useEffect, useRef } from "react";
import { osmStyle } from "./osmStyle";
import { DEFAULT_CENTER, DEFAULT_ZOOM } from "../../../config/map";
import "maplibre-gl/dist/maplibre-gl.css";

function MapView() {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const map = new MapLibreMap({
      container: containerRef.current,
      style: osmStyle,
      center: DEFAULT_CENTER,
      zoom: DEFAULT_ZOOM,
    });
    map.addControl(new NavigationControl(), "top-right");

    return () => map.remove();
  }, []);

  return <div ref={containerRef} className="min-h-0 flex-1" />;
}

export default MapView;
