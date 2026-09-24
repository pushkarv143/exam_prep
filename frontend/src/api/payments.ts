import { apiGet, apiPost } from '@/lib/api'
import type { Checkout, Payment, VerifyPaymentResponse } from '@/types/domain'

export const paymentsApi = {
  createOrder: (seriesId: string) => apiPost<Checkout>('/payments/orders', { seriesId }),
  verify: (orderId: string, paymentId: string, signature: string) =>
    apiPost<VerifyPaymentResponse>('/payments/verify', { orderId, paymentId, signature }),
  /** Only works while the backend runs PAYMENT_PROVIDER=MOCK. */
  mockCheckout: (paymentId: string) => apiPost<VerifyPaymentResponse>(`/payments/${paymentId}/mock-checkout`),
  mine: () => apiGet<Payment[]>('/payments/mine'),
}
