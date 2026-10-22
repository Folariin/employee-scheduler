import { useMutation, useQueryClient } from "@tanstack/react-query";
export function useAction<T, R>(
  action: (data: T) => Promise<R>,
  onSuccess?: (result: R) => void,
) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: action,
    onSuccess: async (result) => {
      await client.invalidateQueries();
      onSuccess?.(result);
    },
    onError: async () => {
      await client.invalidateQueries();
    },
  });
}
