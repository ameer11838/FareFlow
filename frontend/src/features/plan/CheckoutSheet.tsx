import { useEffect, useState } from 'react'
import { paymentsApi } from '../../api'
import type {
  JourneyOption, JourneySearchResponse, PaymentIntent, PaymentRail, PaymentRails,
} from '../../api/types'
import { CardIcon, CheckIcon, CloseIcon, WalletIcon, XrpIcon } from '../../components/Icons'
import { formatCents, formatMinutes } from '../../lib/format'

/** Route checkout: the explicit CHOOSE → PAY handoff in the product loop. */
export function CheckoutSheet({
  option, result, payment, processing, error, onClose, onPay, onRetry,
}: {
  option: JourneyOption
  result: JourneySearchResponse
  payment: PaymentIntent | null
  processing: boolean
  error: string | null
  onClose: () => void
  onPay: (method: PaymentRail) => void
  onRetry: () => void
}) {
  const [method, setMethod] = useState<PaymentRail>('FAREFLOW_WALLET')
  const [rails, setRails] = useState<PaymentRails | null>(null)
  const remaining = result.budgetContext && option.fareCents !== null
    ? result.budgetContext.weeklyBudgetCents
      - result.budgetContext.spentThisWeekCents
      - option.fareCents
    : null
  const failed = payment?.status === 'FAILED'

  useEffect(() => {
    let cancelled = false
    void paymentsApi.rails()
      .then((available) => { if (!cancelled) setRails(available) })
      .catch(() => { if (!cancelled) setRails(null) })
    return () => { cancelled = true }
  }, [])

  return (
    <div className="checkout-backdrop" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !processing) onClose()
    }}>
      <section className="checkout-sheet" role="dialog" aria-modal="true"
               aria-labelledby="checkout-title" data-testid="checkout-sheet">
        <header className="checkout-head">
          <div>
            <span className="checkout-eyebrow">Choose → Pay</span>
            <h2 id="checkout-title">Confirm your transit trip</h2>
          </div>
          <button className="icon-btn" type="button" onClick={onClose}
                  disabled={processing} aria-label="Close checkout">
            <CloseIcon />
          </button>
        </header>

        <div className="checkout-route">
          <div>
            <strong>{result.origin.displayName} → {result.destination.displayName}</strong>
            <span>{option.summary}</span>
          </div>
          <div className="checkout-fare numeric">
            {option.fareCents === null ? 'Fare unavailable' : formatCents(option.fareCents)}
            <span>{formatMinutes(option.totalMinutes)} · {option.transfers === 0
              ? 'Direct' : `${option.transfers} transfer${option.transfers === 1 ? '' : 's'}`}</span>
          </div>
        </div>

        {remaining !== null && (
          <div className={`checkout-budget${remaining < 0 ? ' over' : ''}`}>
            <span>Weekly budget after this trip</span>
            <strong className="numeric">
              {remaining < 0 ? `${formatCents(-remaining)} over` : `${formatCents(remaining)} left`}
            </strong>
          </div>
        )}

        {option.fareCents !== null && (
          <fieldset className="checkout-methods" disabled={processing || failed}>
            <legend>Payment method</legend>
            <label className={`checkout-method${method === 'FAREFLOW_WALLET' ? ' selected' : ''}`}>
              <input type="radio" name="payment-method" value="FAREFLOW_WALLET"
                     checked={method === 'FAREFLOW_WALLET'}
                     onChange={() => setMethod('FAREFLOW_WALLET')} />
              <span className="checkout-method-icon"><WalletIcon /></span>
              <span><strong>FareFlow Wallet</strong><small>Uses your transportation budget</small></span>
              {method === 'FAREFLOW_WALLET' && <CheckIcon />}
            </label>
            <label className={`checkout-method${method === 'SIMULATED_CARD' ? ' selected' : ''}`}>
              <input type="radio" name="payment-method" value="SIMULATED_CARD"
                     checked={method === 'SIMULATED_CARD'}
                     onChange={() => setMethod('SIMULATED_CARD')} />
              <span className="checkout-method-icon"><CardIcon /></span>
              <span><strong>Simulated card</strong><small>No real money or card data</small></span>
              {method === 'SIMULATED_CARD' && <CheckIcon />}
            </label>
            {rails?.rails.includes('XRPL_RLUSD') && (
              <label className={`checkout-method${method === 'XRPL_RLUSD' ? ' selected' : ''}`}>
                <input type="radio" name="payment-method" value="XRPL_RLUSD"
                       checked={method === 'XRPL_RLUSD'}
                       onChange={() => setMethod('XRPL_RLUSD')} />
                <span className="checkout-method-icon"><XrpIcon /></span>
                <span>
                  <strong>Demo RLUSD on XRP Ledger</strong>
                  <small>Public {rails.network?.toLowerCase()} settlement · no-value test asset</small>
                </span>
                {method === 'XRPL_RLUSD' && <CheckIcon />}
              </label>
            )}
          </fieldset>
        )}

        <div className="checkout-assurance">
          {option.fareCents === null ? (
            <>
              <span>No payment will be created</span>
              <span>No payment will be recorded</span>
              <span>Trip recorded as unpriced</span>
            </>
          ) : (
            <>
              <span>Fare recalculated by FareFlow</span>
              <span>Duplicate-payment protection</span>
              <span>Append-only receipt</span>
            </>
          )}
        </div>

        {(error || payment?.failureMessage) && (
          <div className="checkout-error" role="alert">
            <strong>Payment failed</strong>
            <span>{payment?.failureMessage ?? error}</span>
          </div>
        )}

        {failed ? (
          <button className="btn btn-primary checkout-submit" type="button"
                  onClick={onRetry} disabled={processing}>
            {processing ? 'Retrying safely…' : 'Retry payment'}
          </button>
        ) : (
          <button className="btn btn-primary checkout-submit" type="button"
                  onClick={() => onPay(method)} disabled={processing}>
            {processing
              ? method === 'XRPL_RLUSD' ? 'Validating on XRP Ledger…' : 'Authorizing and settling…'
              : option.fareCents === null
                ? 'Record trip without charge'
                : method === 'XRPL_RLUSD'
                  ? `Pay ${formatCents(option.fareCents)} in demo RLUSD`
                  : `Pay ${formatCents(option.fareCents)}`}
          </button>
        )}
        <p className="checkout-fineprint">
          {option.fareCents === null
            ? 'Because no authoritative fare is available, FareFlow records the trip without treating it as free.'
            : 'The browser never sends a fare. FareFlow recalculates it before creating the payment.'}
        </p>
      </section>
    </div>
  )
}
