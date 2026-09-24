import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ApiError, errorMessage } from './errors'

declare module '@tanstack/react-query' {
  interface Register {
    queryMeta: { silent?: boolean }
    mutationMeta: { silent?: boolean }
  }
}

/**
 * Global error policy:
 * - Queries: the first load failure is rendered in place (<ErrorState/>). Background
 *   refetch failures, where stale data is still shown, surface as a toast.
 * - Mutations: toast, unless the mutation handles its own errors (onError) or sets meta.silent.
 * - No retries on 4xx (they will not succeed); up to 2 retries on network and 5xx errors.
 */
export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error, query) => {
      if (query.meta?.silent || query.state.data === undefined) return
      toast.error(errorMessage(error))
    },
  }),
  mutationCache: new MutationCache({
    onError: (error, _vars, _ctx, mutation) => {
      if (mutation.meta?.silent || mutation.options.onError) return
      toast.error(errorMessage(error))
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 2,
    },
  },
})
