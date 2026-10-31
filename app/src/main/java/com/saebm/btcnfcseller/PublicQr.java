package com.saebm.btcnfcseller;

import com.saebm.btcnfcseller.core.BitcoinAddress;

/** Never retain or echo unrecognized QR contents. */
final class PublicQr {
    private PublicQr() { }
    static String address(String payload) {
        return BitcoinAddress.isValid(payload) ? payload : null;
    }
}
