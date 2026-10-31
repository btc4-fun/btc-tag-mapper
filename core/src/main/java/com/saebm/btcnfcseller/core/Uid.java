package com.saebm.btcnfcseller.core;

public final class Uid {
    private Uid() {}
    public static boolean isValid(String value) {
        return value != null && (value.length() == 8 || value.length() == 14 || value.length() == 20)
                && value.matches("[0-9A-F]+");
    }
    public static String fromBytes(byte[] value) {
        if (value == null || (value.length != 4 && value.length != 7 && value.length != 10))
            throw new IllegalArgumentException("UID must contain 4, 7 or 10 bytes");
        char[] hex = "0123456789ABCDEF".toCharArray();
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte b : value) out.append(hex[(b & 255) >>> 4]).append(hex[b & 15]);
        return out.toString();
    }
}
