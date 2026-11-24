import { useEffect } from "react";
import useHealthMutation from "./useHealthMutation";

function useHealthLog() {
  const { data, error } = useHealthMutation();

  useEffect(() => {
    if (data) console.log("[health] backend:", data.status);
  }, [data]);

  useEffect(() => {
    if (error)
      console.error("[health] brak połączenia z backendem:", error.message);
  }, [error]);
}

export default useHealthLog;
