import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { paymentsApi } from '@/api/payments'
import { seriesKeys } from '@/api/series'
import { errorMessage } from '@/lib/errors'
import { openRazorpayCheckout } from '@/lib/razorpay'
import type { Checkout } from '@/types/domain'

export type PurchaseStep = 'idle' | 'creating' | 'awaiting-mock-confirm' | 'paying' | 'verifying' | 'done'

/**
 * Checkout flow for a paid series:
 *   create order (server) → Razorpay Checkout (browser) → verify signature (server) → access granted.
 * With the backend's MOCK provider, the Razorpay step is replaced by a confirmation
 * dialog and the mock-checkout endpoint.
 *
 * Access is granted by the server on a verified signature or on the webhook, never by
 * this client. If the tab closes mid-payment, the webhook still completes the enrollment.
 */
export function usePurchase(seriesId: string) {
  const qc = useQueryClient()
  const [step, setStep] = useState<PurchaseStep>('idle')
  const [checkout, setCheckout] = useState<Checkout | null>(null)

  const finish = async () => {
    setStep('done')
    await qc.invalidateQueries({ queryKey: seriesKeys.all })
    toast.success('Payment successful. You now have access to all tests in this series.')
  }

  const fail = (e: unknown) => {
    setStep('idle')
    const msg = errorMessage(e)
    if (msg === 'Payment cancelled') {
      toast.info('Payment cancelled. You have not been charged.')
    } else {
      toast.error(msg)
    }
  }

  const start = async () => {
    try {
      setStep('creating')
      const order = await paymentsApi.createOrder(seriesId)
      setCheckout(order)
      if (order.mock) {
        setStep('awaiting-mock-confirm')
        return
      }
      setStep('paying')
      const result = await openRazorpayCheckout(order)
      setStep('verifying')
      await paymentsApi.verify(result.razorpay_order_id, result.razorpay_payment_id, result.razorpay_signature)
      await finish()
    } catch (e) {
      fail(e)
    }
  }

  const confirmMock = async () => {
    if (!checkout) return
    try {
      setStep('verifying')
      await paymentsApi.mockCheckout(checkout.paymentId)
      await finish()
    } catch (e) {
      fail(e)
    }
  }

  const cancelMock = () => {
    setStep('idle')
    toast.info('Payment cancelled. You have not been charged.')
  }

  return { step, checkout, start, confirmMock, cancelMock, busy: step !== 'idle' && step !== 'done' && step !== 'awaiting-mock-confirm' }
}
