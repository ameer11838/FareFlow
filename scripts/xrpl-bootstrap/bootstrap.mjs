/**
 * Stands up the two accounts the RLUSD rail needs, on an XRPL test network.
 *
 * Run once, then paste the printed values into the repo-root .env.
 *
 *   cd scripts/xrpl-bootstrap && npm install && npm start
 *
 * What it creates:
 *   - an ISSUER account, which mints the RLUSD-coded token riders pay with
 *   - a TREASURY account, which receives fares and trusts that issuer
 *
 * Ripple publishes an official RLUSD issuer on Testnet, but its faucet is an
 * interactive, authenticated developer tool rather than an application funding
 * API. FareFlow therefore self-issues an RLUSD-coded demo asset so a new test
 * rider can complete checkout without leaving the app. The mechanics —
 * trustlines, issued-currency payments, consensus and public receipts — are real;
 * the asset is explicitly not Ripple-issued RLUSD and has no value.
 */
import { Client, Wallet } from 'xrpl'

const NETWORK = process.env.XRPL_NETWORK?.toUpperCase() === 'DEVNET' ? 'DEVNET' : 'TESTNET'
const WS = NETWORK === 'DEVNET'
  ? 'wss://s.devnet.rippletest.net:51233'
  : 'wss://s.altnet.rippletest.net:51233'

// RLUSD is five characters, so it travels as a 160-bit hex currency code.
const RLUSD = '524C555344000000000000000000000000000000'
const TRUST_LIMIT = '100000'

const client = new Client(WS)

async function submit(wallet, tx, label) {
  const prepared = await client.autofill(tx)
  const signed = wallet.sign(prepared)
  const res = await client.submitAndWait(signed.tx_blob)
  const code = res.result.meta?.TransactionResult
  if (code !== 'tesSUCCESS') throw new Error(`${label} failed: ${code}`)
  console.log(`  ${label}: ${code}  ${res.result.hash}`)
  return res
}

async function main() {
  console.log(`Connecting to XRPL ${NETWORK}…`)
  await client.connect()

  console.log('\nFunding issuer account from the faucet…')
  const { wallet: issuer } = await client.fundWallet()
  console.log(`  issuer:   ${issuer.classicAddress}`)

  console.log('Funding treasury account from the faucet…')
  const { wallet: treasury } = await client.fundWallet()
  console.log(`  treasury: ${treasury.classicAddress}`)

  // The issuer must allow its token to move between third parties; without
  // rippling enabled, a rider could hold the token but never spend it onward.
  console.log('\nEnabling rippling on the issuer (asfDefaultRipple)…')
  await submit(issuer, {
    TransactionType: 'AccountSet',
    Account: issuer.classicAddress,
    SetFlag: 8,
  }, 'AccountSet')

  console.log('\nTrusting the issuer from the treasury…')
  await submit(treasury, {
    TransactionType: 'TrustSet',
    Account: treasury.classicAddress,
    LimitAmount: { currency: RLUSD, issuer: issuer.classicAddress, value: TRUST_LIMIT },
  }, 'TrustSet')

  console.log('\n' + '='.repeat(68))
  console.log('Add these to the repo-root .env, then restart the backend:')
  console.log('='.repeat(68))
  console.log(`XRPL_NETWORK=${NETWORK}`)
  console.log(`XRPL_RLUSD_ISSUER=${issuer.classicAddress}`)
  console.log(`XRPL_ISSUER_SEED=${issuer.seed}`)
  console.log(`XRPL_TREASURY_ADDRESS=${treasury.classicAddress}`)
  console.log('='.repeat(68))
  console.log('XRPL_ISSUER_SEED is a signing key. It belongs only in the')
  console.log('gitignored .env, and only ever for a test network.')

  await client.disconnect()
}

main().catch(async (error) => {
  console.error('\nBootstrap failed:', error.message)
  try { await client.disconnect() } catch { /* already closed */ }
  process.exit(1)
})
