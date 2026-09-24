import type { Checkout } from '@/types/domain'

/** The three values Razorpay Checkout returns on success. */
export interface RazorpaySuccess {
  razorpay_order_id: string
  razorpay_payment_id: string
  razorpay_signature: string
}

interface RazorpayInstance {
  open: () => void
  on: (event: 'payment.failed', handler: (resp: { error: { description?: string } }) => void) => void
}

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => RazorpayInstance
  }
}

const SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js'
let loading: Promise<void> | null = null

/** Loads Razorpay's checkout.js once, on demand (it is only needed when someone buys). */
function loadScript(): Promise<void> {
  if (window.Razorpay) return Promise.resolve()
  loading ??= new Promise<void>((resolve, reject) => {
    const s = document.createElement('script')
    s.src = SCRIPT_URL
    s.async = true
    s.onload = () => resolve()
    s.onerror = () => {
      loading = null
      reject(new Error('Could not load the payment window. Check your connection and try again.'))
    }
    document.body.appendChild(s)
  })
  return loading
}

/**
 * Opens Razorpay Checkout. It resolves with the signed result, or rejects when the user
 * closes the window or the payment fails. The caller must verify the result server-side;
 * this promise alone proves nothing.
 */
export async function openRazorpayCheckout(checkout: Checkout): Promise<RazorpaySuccess> {
  await loadScript()
  return new Promise<RazorpaySuccess>((resolve, reject) => {
    const rzp = new window.Razorpay!({
      key: checkout.keyId,
      order_id: checkout.orderId,
      amount: checkout.amountMinor,
      currency: checkout.currency,
      name: checkout.name,
      description: checkout.description,
      prefill: { name: checkout.prefillName, email: checkout.prefillEmail, contact: checkout.prefillContact },
      theme: { color: '#4338ca' },
      handler: (resp: RazorpaySuccess) => resolve(resp),
      modal: { ondismiss: () => reject(new Error('Payment cancelled')) },
    })
    rzp.on('payment.failed', (resp) => reject(new Error(resp.error.description ?? 'Payment failed')))
    rzp.open()
  })
}
