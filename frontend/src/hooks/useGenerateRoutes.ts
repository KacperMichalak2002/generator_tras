import { useMutation } from "@tanstack/react-query";
import { generateRoutes } from "../services/routeService";
import { useRouteStore } from "../stores/routeStore";

function useGenerateRoutes() {
  const mutation = useMutation({
    mutationFn: generateRoutes,
    onSuccess: (data) => {
      if (data) useRouteStore.getState().setRoutes(data);
    },
  });

  return {
    generate: mutation.mutate,
    isPending: mutation.isPending,
    error: mutation.error,
  };
}

export default useGenerateRoutes;
