import { faRoute } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useState, type FormEvent } from "react";
import type {
  RouteParams,
  SubprofileId,
  VehicleType,
} from "../../../types/route";
import { clamp, findSubprofile, PROFILES, VEHICLES } from "./routeProfiles";

const group = "flex min-w-0 flex-col gap-1.5";
const label = "mb-1.5 text-sm font-semibold";
const segmented = "flex overflow-hidden rounded-lg border border-line";
const segment =
  "flex flex-1 cursor-pointer items-center justify-center gap-1.5 bg-white px-1 py-2 not-first:border-l not-first:border-line aria-pressed:bg-primary-soft aria-pressed:font-semibold aria-pressed:text-primary";

const DEFAULT_PARAMS: RouteParams = {
  vehicle: "bike",
  subprofile: "bike_road",
  distanceKm: 50,
  curviness: 50,
  avoidMainRoads: true,
};

function RouteForm() {
  const [params, setParams] = useState<RouteParams>(DEFAULT_PARAMS);
  const sub = findSubprofile(params.subprofile);

  function update<K extends keyof RouteParams>(key: K, value: RouteParams[K]) {
    setParams((p) => ({ ...p, [key]: value }));
  }

  function selectSubprofile(id: SubprofileId, vehicle = params.vehicle) {
    const s = findSubprofile(id);
    setParams((p) => ({
      ...p,
      vehicle,
      subprofile: id,
      distanceKm: clamp(p.distanceKm, s.minKm, s.maxKm),
    }));
  }

  function selectVehicle(vehicle: VehicleType) {
    selectSubprofile(PROFILES[vehicle].subprofiles[0].id, vehicle);
  }

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    console.log("[route] generuj (dummy):", params);
  }

  return (
    <form
      className="flex flex-col gap-4.5 accent-primary"
      onSubmit={handleSubmit}
    >
      <fieldset className={group}>
        <legend className={label}>Pojazd</legend>
        <div className={segmented}>
          {VEHICLES.map((v) => (
            <button
              key={v}
              type="button"
              className={segment}
              aria-pressed={params.vehicle === v}
              onClick={() => selectVehicle(v)}
            >
              <FontAwesomeIcon icon={PROFILES[v].icon} />
              {PROFILES[v].label}
            </button>
          ))}
        </div>
      </fieldset>

      <fieldset className={group}>
        <legend className={label}>Rodzaj</legend>
        <div className={segmented}>
          {PROFILES[params.vehicle].subprofiles.map((s) => (
            <button
              key={s.id}
              type="button"
              className={segment}
              aria-pressed={params.subprofile === s.id}
              onClick={() => selectSubprofile(s.id)}
            >
              {s.label}
            </button>
          ))}
        </div>
      </fieldset>

      <div className={group}>
        <label htmlFor="distance" className={label}>
          Dystans
        </label>
        <div className="flex items-center gap-2">
          <input
            id="distance"
            type="range"
            className="min-w-0 flex-1"
            min={sub.minKm}
            max={sub.maxKm}
            value={params.distanceKm}
            onChange={(e) => update("distanceKm", Number(e.target.value))}
          />
          <input
            type="number"
            aria-label="Dystans w km"
            className="w-18 rounded-md border border-line px-1.5 py-1"
            min={sub.minKm}
            max={sub.maxKm}
            value={params.distanceKm}
            onChange={(e) => update("distanceKm", Number(e.target.value))}
            onBlur={() =>
              update(
                "distanceKm",
                clamp(params.distanceKm, sub.minKm, sub.maxKm),
              )
            }
          />
          <span>km</span>
        </div>
        <small className="text-muted">
          Zakres {sub.minKm}–{sub.maxKm} km
        </small>
      </div>

      <div className={group}>
        <label htmlFor="curviness" className={label}>
          Zakręty
        </label>
        <input
          id="curviness"
          type="range"
          min={0}
          max={100}
          value={params.curviness}
          onChange={(e) => update("curviness", Number(e.target.value))}
        />
        <div className="flex justify-between text-muted">
          <small>mało</small>
          <small>dużo</small>
        </div>
      </div>

      <label className="flex items-center gap-2">
        <input
          type="checkbox"
          checked={params.avoidMainRoads}
          onChange={(e) => update("avoidMainRoads", e.target.checked)}
        />
        Unikaj dróg głównych
      </label>

      <div className={group}>
        <span className={label}>Start</span>
        <small className="text-muted">
          Kliknij na mapie, aby wybrać punkt startu (wkrótce)
        </small>
      </div>

      <button
        type="submit"
        className="flex cursor-pointer items-center justify-center gap-2 rounded-full bg-primary p-3 font-semibold text-white hover:bg-primary-hover"
      >
        <FontAwesomeIcon icon={faRoute} />
        Generuj trasę
      </button>
    </form>
  );
}

export default RouteForm;
