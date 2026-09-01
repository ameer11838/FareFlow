import { formatCents } from '../lib/format'

/**
 * The budget-status lede, shared by Insights and Wallet.
 *
 * <p>Both screens open on the same subject and used to answer it with their own
 * hand-rolled block. They have converged so that a rider who learns to read one
 * reads the other, and so there is one place to change it.
 *
 * <p>They do not, however, lead with the same number, and that is deliberate.
 * Insights asks "how is the week tracking", so it leads with spend against
 * budget. Wallet is opened before a trip to ask "can I afford this", so it
 * leads with what is left. Same block, same colours, different subject — which
 * is what `lead` selects.
 *
 * <p>The status colour is semantic and never the only carrier: the verdict is
 * always written out beside it. Green under, amber close, coral over.
 */

export type BudgetStatus = 'none' | 'under' | 'close' | 'over'

/**
 * Judged on where the week is *heading* when a projection exists, not only on
 * what has been spent so far. A week that is 60% spent on a Tuesday is not on
 * track, and reporting it as such is the reassuring kind of wrong.
 */
export function budgetStatus(
  spentCents: number, budgetCents: number | null, projectedCents?: number | null,
): BudgetStatus {
  if (budgetCents === null || budgetCents <= 0) return 'none'
  const reference = projectedCents ?? spentCents
  if (reference > budgetCents) return 'over'
  return reference > budgetCents * 0.9 ? 'close' : 'under'
}

const VERDICT_WORD: Record<BudgetStatus, string> = {
  none: 'No budget set',
  under: 'On track',
  close: 'Running close',
  over: 'Over budget',
}

export function BudgetLede({
  lead = 'spent', label, spentCents, budgetCents, remainingCents, projectedCents = null,
  detail, aside, footer, testId = 'budget-lede',
}: {
  /** Which figure gets the large type. See the note above. */
  lead?: 'spent' | 'remaining'
  label?: string
  spentCents: number
  budgetCents: number | null
  /** Only needed for `lead="remaining"`; defaults to budget − spent. */
  remainingCents?: number | null
  /** Where the week is heading, which is what the verdict is judged on. */
  projectedCents?: number | null
  detail?: React.ReactNode
  aside?: React.ReactNode
  footer?: React.ReactNode
  testId?: string
}) {
  const status = budgetStatus(spentCents, budgetCents, projectedCents)
  const ratio = budgetCents !== null && budgetCents > 0
    ? Math.min(spentCents / budgetCents, 1) : null
  const remaining = remainingCents ?? (budgetCents === null ? null : budgetCents - spentCents)
  const over = remaining !== null && remaining < 0

  const leadsOnRemaining = lead === 'remaining' && remaining !== null
  const headline = leadsOnRemaining ? Math.abs(remaining) : spentCents
  const resolvedLabel = label ?? (leadsOnRemaining ? 'Weekly transportation' : 'Spent this week')

  return (
    <section className={`lede lede-${status}`} data-testid={testId}>
      <div className="lede-main">
        <span className="lede-label">{resolvedLabel}</span>

        <div className="lede-figure">
          <span className="lede-value numeric">{formatCents(headline)}</span>
          {leadsOnRemaining
            ? <span className="lede-of">{over ? 'over budget' : 'remaining'}</span>
            : budgetCents !== null
              && <span className="lede-of numeric">of {formatCents(budgetCents)}</span>}
        </div>

        {ratio !== null && (
          <div className="lede-meter" role="progressbar" aria-valuemin={0}
               aria-valuemax={budgetCents!} aria-valuenow={spentCents}
               aria-label={`${formatCents(spentCents)} spent of ${formatCents(budgetCents!)}`}>
            <span className="lede-meter-fill" style={{ width: `${ratio * 100}%` }} />
          </div>
        )}

        {/* The word and the amount are separate elements: the word is the
            verdict a reader takes away, the amount is the evidence for it. */}
        <span className="lede-verdict">
          <strong className="lede-verdict-word">{VERDICT_WORD[status]}</strong>
          {budgetCents !== null && (
            <span className="lede-verdict-amount numeric">
              {leadsOnRemaining
                ? `${formatCents(spentCents)} spent of ${formatCents(budgetCents)}`
                : over
                  ? `${formatCents(Math.abs(remaining!))} over`
                  : `${formatCents(remaining!)} left this week`}
            </span>
          )}
        </span>

        {detail && <span className="lede-detail">{detail}</span>}
      </div>

      {aside && <div className="lede-aside">{aside}</div>}
      {footer}
    </section>
  )
}

/**
 * The lede before a budget exists.
 *
 * <p>Deliberately not $0.00 with a green "on track" — there is no budget to be
 * on track against, and saying otherwise would be the page inventing a fact.
 */
export function NoBudgetLede({ action }: { action?: React.ReactNode }) {
  return (
    <section className="lede lede-none" data-testid="budget-lede">
      <div className="lede-main">
        <span className="lede-label">Weekly transportation</span>
        <div className="lede-figure">
          <span className="lede-value lede-value-unset">Set a weekly budget</span>
        </div>
        <span className="lede-detail">
          FareFlow tracks spending against a weekly transportation budget, and leans
          toward cheaper routes as you approach it. Until you set one there is no
          balance to show.
        </span>
        {action}
      </div>
    </section>
  )
}
