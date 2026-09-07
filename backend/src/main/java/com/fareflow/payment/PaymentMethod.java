package com.fareflow.payment;

/** Payment rails currently implemented by the provider-neutral payment domain. */
public enum PaymentMethod {
    FAREFLOW_WALLET,
    SIMULATED_CARD,
    /**
     * RLUSD on the XRP Ledger. The only rail whose reference is verifiable by
     * someone who does not trust FareFlow: the settlement is a public transaction.
     */
    XRPL_RLUSD
}
