package com.saebm.btcnfcseller.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class BitcoinAddressTest {
    @Test public void excludesBothPublicPreviewWallets() {
        assertFalse(BitcoinAddress.isValid("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"));
        assertFalse(BitcoinAddress.isValid("bc1qzmtrqsfuaf6l6kkcsseumq26ukaphfj9skkug6"));
    }
    @Test public void rejectsOtherWitnessKindsAndBech32mChecksums() {
        for (String v : new String[]{
                "bc1qqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqsw9e2a9",
                "bc1pqyqszqgpqyqszqgpqyqszqgpqyqszqgp0hg7y6",
                "bc1qqyqszqgpqyqszqgpqyqszqgpqyqszqgp340evn"}) assertFalse(BitcoinAddress.isValid(v));
    }
    // bech32.encode("bc", 0, bytes([1])*20); no key generation or funding.
    static final String A = "bc1qqyqszqgpqyqszqgpqyqszqgpqyqszqgpyfl4f3";
    static final String B = "bc1qqgpqyqszqgpqyqszqgpqyqszqgpqyqsz4desz8";
    @Test public void acceptsCanonicalMainnetWitness20ByteChecksumOnly() {
        assertTrue(BitcoinAddress.isValid(A));
        assertTrue(BitcoinAddress.isValid(B));
        for (String v : new String[]{null,"",A.toUpperCase()," "+A,A+"\n",A.substring(0,41)+"q",
                "tb"+A.substring(2),"bc1p"+A.substring(4),"bitcoin:"+A,A+"q",A.substring(1)})
            assertFalse(String.valueOf(v), BitcoinAddress.isValid(v));
    }
}
