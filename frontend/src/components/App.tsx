import useHealthLog from "../hooks/useHealthLog";
import MapView from "./feature/map/MapView";
import RoutePanel from "./feature/routePanel/RoutePanel";

function App() {
  useHealthLog();
  return (
    <main className="relative flex h-full overflow-hidden">
      <MapView />
      <RoutePanel />
    </main>
  );
}

export default App;
