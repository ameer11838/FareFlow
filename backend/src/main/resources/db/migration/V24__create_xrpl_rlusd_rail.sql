-- RLUSD settlement on the XRP Ledger, as a third payment rail.
--
-- Custody is deliberate and narrow. FareFlow holds one XRPL Testnet account per
-- rider, funded from the public faucet, and signs their fare payments server-side.
-- That is a real custodial arrangement and is treated as one: the key material is
-- encrypted at rest, the network is pinned to a test network in configuration, and
-- the account is created lazily so a rider who never picks this rail never has one.
--
-- Testnet is not a limitation to be lifted later without thought. Mainnet custody
-- of a real stablecoin is a regulated activity, and this project is explicitly a
-- simulation; the network column exists so a stored wallet can never be silently
-- reinterpreted against a different ledger than the one that created it.

CREATE TABLE xrpl_wallets
(
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT      NOT NULL UNIQUE REFERENCES users (id),

    -- The classic r-address. Public by nature; safe to show and to link out to.
    classic_address        TEXT        NOT NULL UNIQUE,
    public_key_hex         TEXT        NOT NULL,

    -- AES-GCM ciphertext of the seed entropy, plus its nonce. Never the seed in
    -- the clear, and never logged: a row leak alone must not move funds.
    encrypted_entropy      BYTEA       NOT NULL,
    entropy_nonce          BYTEA       NOT NULL,
    key_algorithm          TEXT        NOT NULL DEFAULT 'ED25519',

    -- Which ledger this account exists on. A wallet is meaningless without it.
    network                TEXT        NOT NULL,

    -- A trustline to the RLUSD issuer must exist before the account can hold the
    -- asset at all, so provisioning is a two-step process worth recording.
    funded_at              TIMESTAMPTZ NULL,
    trustline_set_at       TIMESTAMPTZ NULL,
    rlusd_issuer_address   TEXT        NULL,

    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    version                BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT chk_xrpl_wallet_address CHECK (classic_address ~ '^r[1-9A-HJ-NP-Za-km-z]{24,34}$'),
    CONSTRAINT chk_xrpl_wallet_algorithm CHECK (key_algorithm IN ('ED25519', 'SECP256K1')),
    -- Mainnet is absent on purpose; see the note above.
    CONSTRAINT chk_xrpl_wallet_network CHECK (network IN ('TESTNET', 'DEVNET')),
    CONSTRAINT chk_xrpl_wallet_material CHECK
        (length(encrypted_entropy) > 0 AND length(entropy_nonce) > 0),
    -- A trustline cannot precede the funding that made the account exist.
    CONSTRAINT chk_xrpl_wallet_trustline_order CHECK
        (trustline_set_at IS NULL OR funded_at IS NOT NULL),
    CONSTRAINT chk_xrpl_wallet_trustline_issuer CHECK
        ((trustline_set_at IS NULL) = (rlusd_issuer_address IS NULL))
);

-- The new rail, alongside the existing wallet and simulated card.
ALTER TABLE payment_intents
    DROP CONSTRAINT chk_payment_method;

ALTER TABLE payment_intents
    ADD CONSTRAINT chk_payment_method CHECK
        (payment_method IN ('FAREFLOW_WALLET', 'SIMULATED_CARD', 'XRPL_RLUSD'));

-- The on-ledger transaction hash for a settled RLUSD payment. Kept distinct from
-- provider_reference because this one is independently verifiable: anyone can
-- resolve it on a public explorer, which is the entire point of settling here.
ALTER TABLE payment_intents
    ADD COLUMN xrpl_transaction_hash TEXT NULL,
    ADD COLUMN xrpl_network TEXT NULL;

ALTER TABLE payment_intents
    ADD CONSTRAINT chk_payment_xrpl_hash CHECK
        (xrpl_transaction_hash IS NULL OR xrpl_transaction_hash ~ '^[0-9A-F]{64}$'),
    ADD CONSTRAINT chk_payment_xrpl_network CHECK
        (xrpl_network IS NULL OR xrpl_network IN ('TESTNET', 'DEVNET')),
    -- A hash without its network cannot be resolved against the right explorer.
    ADD CONSTRAINT chk_payment_xrpl_pair CHECK
        ((xrpl_transaction_hash IS NULL) = (xrpl_network IS NULL)),
    -- Only the RLUSD rail settles on-ledger; no other method may claim a hash.
    ADD CONSTRAINT chk_payment_xrpl_method CHECK
        (xrpl_transaction_hash IS NULL OR payment_method = 'XRPL_RLUSD');

CREATE INDEX idx_payment_xrpl_hash ON payment_intents (xrpl_transaction_hash)
    WHERE xrpl_transaction_hash IS NOT NULL;
