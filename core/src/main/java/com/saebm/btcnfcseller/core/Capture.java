package com.saebm.btcnfcseller.core;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable capture: never normalizes or regenerates the recorded timestamp. */
public final class Capture {
    private static final Pattern TIMESTAMP = Pattern.compile(
            "([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?)(Z|[+-](?:[01][0-9]|2[0-3]):[0-5][0-9])");
    private final LocalDateTime utc;
    public final String uid, address, capturedAt;
    public String utcDate() { return utc.toLocalDate().toString(); }
    public Capture(String uid, String address, String capturedAt) {
        if (!Uid.isValid(uid) || !BitcoinAddress.isValid(address))
            throw new IllegalArgumentException("Invalid UID or inventory address");
        if (capturedAt == null) throw new IllegalArgumentException("Invalid capture timestamp");
        Matcher match = TIMESTAMP.matcher(capturedAt);
        if (!match.matches()) throw new IllegalArgumentException("Invalid capture timestamp");
        try {
            LocalDateTime local = LocalDateTime.parse(match.group(1));
            if (local.getYear() < 1) throw new IllegalArgumentException("Invalid capture year");
            String zone = match.group(2);
            int offset = 0;
            if (!zone.equals("Z")) {
                offset = Integer.parseInt(zone.substring(1, 3)) * 3600
                        + Integer.parseInt(zone.substring(4, 6)) * 60;
                if (zone.charAt(0) == '-') offset = -offset;
            }
            // Python accepts offsets up to 23:59; java.time.ZoneOffset stops at 18h.
            utc = local.minusSeconds(offset);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid capture timestamp", e);
        }
        this.uid = uid; this.address = address; this.capturedAt = capturedAt;
    }
}
