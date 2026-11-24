import useHealthLog from "../hooks/useHealthLog";
import "../styles/App.css";
import MapView from "./feature/map/MapView";

function App() {
  useHealthLog();
  return (
    <main className="app">
      <MapView />
    </main>
  );
}

export default App;
