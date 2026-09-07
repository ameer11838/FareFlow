import type { PaymentIntent } from '../../../api/types'

/**
 * Independent confirmation that a fare really settled on the XRP Ledger.
 *
 * This is the point of the rail. Every other payment method asks the rider to
 * take FareFlow's word that money moved; here the browser asks a public network
 * directly, over a connection FareFlow's server is not part of. If the two ever
 * disagreed, the ledger would be right.
 *
 * Read-only by construction. `xrpl.js` runs here and never sees a key: signing is
 * custodial and happens server-side, so the worst this code can do is report.
 */

export type LedgerCheck =
  | { state: 'checking' }
  | { state: 'confirmed'; validated: boolean; ledgerIndex: number | null }
  | { state: 'unavailable'; reason: string }

const RPC_URL: Record<string, string> = {
  TESTNET: 'wss://s.altnet.rippletest.net:51233',
  DEVNET: 'wss://s.devnet.rippletest.net:51233',
}

/**
 * Looks the transaction up on the network it was recorded against.
 *
 * Never throws. A public node being unreachable is not evidence the payment
 * failed — FareFlow already holds a hash the server watched succeed — so an
 * unreachable node reports `unavailable` and the receipt stands on its own.
 */
export async function verifyOnLedger(payment: PaymentIntent): Promise<LedgerCheck> {
  const hash = payment.xrplTransactionHash
  const network = payment.xrplNetwork
  if (!hash || !network) {
    return { state: 'unavailable', reason: 'This payment did not settle on-ledger.' }
  }

  const url = RPC_URL[network]
  if (!url) {
    return { state: 'unavailable', reason: `Unknown XRPL network ${network}.` }
  }

  // Imported on demand: the SDK is large and only riders who chose this rail,
  // and then opened the receipt, ever need it.
  const { Client } = await import('xrpl')
  const client = new Client(url, { connectionTimeout: 8_000 })

  try {
    await client.connect()
    const response = await client.request({ command: 'tx', transaction: hash })
    const result = response.result as {
      validated?: boolean
      ledger_index?: number
    }
    return {
      state: 'confirmed',
      validated: result.validated === true,
      ledgerIndex: typeof result.ledger_index === 'number' ? result.ledger_index : null,
    }
  } catch (caught) {
    const detail = caught instanceof Error ? caught.message : String(caught)
    return { state: 'unavailable', reason: detail }
  } finally {
    // Disconnect failures are not worth surfacing: the answer is already in hand.
    try {
      await client.disconnect()
    } catch {
      /* already closed */
    }
  }
}
