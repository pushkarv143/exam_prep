import { useQuery } from '@tanstack/react-query'
import { apiGet } from '@/lib/api'
import type { Exam } from '@/types/domain'

export const catalogApi = {
  exams: () => apiGet<Exam[]>('/public/catalog/exams'),
}

/** Exams change rarely; cache for 10 minutes. */
export function useExams() {
  return useQuery({ queryKey: ['exams'], queryFn: catalogApi.exams, staleTime: 10 * 60_000 })
}
