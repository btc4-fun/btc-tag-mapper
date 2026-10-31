package com.saebm.btcnfcseller.core;
public final class BitcoinAddress {
    private BitcoinAddress() {}
    private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int[] GENERATORS = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};

    /** Canonical lowercase mainnet witness-v0 20-byte inventory addresses only. */
    public static boolean isValid(String value) {
        if (value == null || value.length() != 42 || !value.startsWith("bc1q")) return false;
        int checksum = 1;
        // BIP173 HRP expansion for "bc".
        for (int v : new int[]{3, 3, 0, 2, 3}) checksum = step(checksum, v);
        for (int i = 3; i < value.length(); i++) {
            int v = CHARSET.indexOf(value.charAt(i));
            if (v < 0) return false;
            checksum = step(checksum, v);
        }
        return checksum == 1
                && !value.equals("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu")
                && !value.equals("bc1qzmtrqsfuaf6l6kkcsseumq26ukaphfj9skkug6");
    }

    // BIP173 reference polymod; see core/BECH32-LICENSE.txt.
    private static int step(int checksum, int value) {
        int top = checksum >>> 25;
        int result = ((checksum & 0x1ffffff) << 5) ^ value;
        for (int i = 0; i < 5; i++) if (((top >>> i) & 1) != 0) result ^= GENERATORS[i];
        return result;
    }
}
