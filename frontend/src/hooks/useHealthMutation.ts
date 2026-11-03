import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../api/apiClient";

export type HealthResponse = {
  status: string;
};

async function getHealth(): Promise<HealthResponse> {
  const { data } = await apiClient.get<HealthResponse>("/health");
  return data;
}

function useHealthMutation() {
  return useQuery({
    queryKey: ["health"],
    queryFn: getHealth,
  });
}

export default useHealthMutation;
