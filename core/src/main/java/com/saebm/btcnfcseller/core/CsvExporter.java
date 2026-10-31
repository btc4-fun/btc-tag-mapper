package com.saebm.btcnfcseller.core;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class CsvExporter {
    private CsvExporter() {}
    private static final String HEADER = "uid,address,captured_at\n";
    private static Part part(String date, int number, StringBuilder text) {
        return new Part(date+String.format(Locale.ROOT,"-part-%03d.csv",number),text.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static final class Part {
        public final String filename;
        public final byte[] bytes;
        private Part(String filename, byte[] bytes) { this.filename=filename; this.bytes=bytes; }
    }
    public static List<Part> export(List<Capture> captures, String date) {
        return export(captures,date,5000,1024*1024);
    }
    /** Lower limits allow deterministic boundary checks; wire limits cannot be raised. */
    public static List<Part> export(List<Capture> captures, String date, int maxRows, int maxBytes) {
        if (maxRows < 1 || maxRows > 5000 || maxBytes < 1 || maxBytes > 1024*1024)
            throw new IllegalArgumentException("Invalid export limits");
        if (captures == null || date == null || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("Invalid captures or UTC date");
        try {
            if (java.time.LocalDate.parse(date).getYear() < 1) throw new IllegalArgumentException("Invalid UTC year");
        } catch (java.time.format.DateTimeParseException e) { throw new IllegalArgumentException("Invalid UTC date",e); }
        for (Capture c : captures) if (c == null) throw new IllegalArgumentException("Null capture");
        Set<String> uids = new HashSet<>(), addresses = new HashSet<>();
        for (Capture c : captures) if (c.utcDate().equals(date)) {
            if (!uids.add(c.uid) || !addresses.add(c.address))
                throw new IllegalArgumentException("Duplicate UID or address in export day");
        }
        List<Part> parts = new ArrayList<>();
        StringBuilder text = new StringBuilder(HEADER);
        int rows = 0;
        int bytes = HEADER.getBytes(StandardCharsets.UTF_8).length;
        for (Capture c : captures) {
            if (!c.utcDate().equals(date)) continue;
            String row = c.uid+","+c.address+","+c.capturedAt+"\n";
            int rowBytes = row.getBytes(StandardCharsets.UTF_8).length;
            if (HEADER.length()+rowBytes > maxBytes) throw new IllegalArgumentException("Capture exceeds byte limit");
            if (rows == maxRows || bytes+rowBytes > maxBytes) {
                parts.add(part(date, parts.size()+1, text));
                text = new StringBuilder(HEADER);
                rows = 0;
                bytes = HEADER.getBytes(StandardCharsets.UTF_8).length;
            }
            text.append(row);
            rows++;
            bytes += rowBytes;
        }
        if (rows != 0) parts.add(part(date, parts.size()+1, text));
        return parts;
    }
}
