/** Wire format of every backend response (see ApiResponse.java). */
export interface FieldViolation {
  field: string
  message: string
}

export interface ApiErrorBody {
  code: string
  message: string
  violations?: FieldViolation[]
  requestId?: string
}

export interface ApiEnvelope<T> {
  success: boolean
  data?: T
  error?: ApiErrorBody
  timestamp: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}
