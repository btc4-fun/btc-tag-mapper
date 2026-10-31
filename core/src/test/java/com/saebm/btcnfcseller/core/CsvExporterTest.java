package com.saebm.btcnfcseller.core;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class CsvExporterTest {
    @Test public void groupsByUtcDayAndReturnsNoHeaderOnlyFiles() {
        Capture before = capture("0012ABFF",BitcoinAddressTest.A,"2026-10-31T00:00:00+03:30");
        Capture inside = capture("0012ABFE",BitcoinAddressTest.B,"2026-11-01T01:00:00+03:30");
        List<CsvExporter.Part> parts = CsvExporter.export(Arrays.asList(before,inside), DAY);
        assertEquals(1, parts.size());
        assertEquals("uid,address,captured_at\n"+inside.uid+","+inside.address+","+inside.capturedAt+"\n",text(parts.get(0)));
        assertTrue(CsvExporter.export(Arrays.asList(before),DAY).isEmpty());
        assertTrue(CsvExporter.export(Collections.emptyList(),DAY).isEmpty());
    }
    @Test public void rejectsInvalidDateAndNullInputRatherThanDroppingRows() {
        for (String date : new String[]{null,"", "2026-02-29", "2026-1-1", "../2026-10-31", "0000-01-01"})
            assertThrows(IllegalArgumentException.class, () -> CsvExporter.export(Collections.emptyList(),date));
        assertThrows(IllegalArgumentException.class, () -> CsvExporter.export(null,DAY));
        assertThrows(IllegalArgumentException.class, () -> CsvExporter.export(Arrays.asList((Capture)null),DAY));
    }
    @Test public void splitsAtFiveThousandRowsWithoutDrops() throws Exception {
        List<String> addresses = java.nio.file.Files.readAllLines(java.nio.file.Paths.get("src/test/resources/synthetic-addresses.txt").toFile().exists()
                ? java.nio.file.Paths.get("src/test/resources/synthetic-addresses.txt") : java.nio.file.Paths.get("core/src/test/resources/synthetic-addresses.txt"));
        List<Capture> input = new ArrayList<>();
        for (int i=0;i<5001;i++) input.add(capture(String.format(Locale.ROOT,"%08X",i),addresses.get(i),DAY+"T00:00:00Z"));
        assertEquals(1,CsvExporter.export(input.subList(0,4999),DAY).size());
        assertEquals(1,CsvExporter.export(input.subList(0,5000),DAY).size());
        List<CsvExporter.Part> parts = CsvExporter.export(input,DAY);
        assertEquals(2,parts.size());
        assertEquals("2026-10-31-part-002.csv",parts.get(1).filename);
        List<String> actual = new ArrayList<>();
        for (CsvExporter.Part p : parts) {
            assertTrue(p.bytes.length <= 1024*1024);
            String[] lines = text(p).split("\n");
            assertTrue(lines.length <= 5001);
            actual.addAll(Arrays.asList(lines).subList(1,lines.length));
        }
        List<String> expected = new ArrayList<>();
        for (Capture c : input) expected.add(c.uid+","+c.address+","+c.capturedAt);
        assertEquals(expected,actual);
    }
    @Test public void countsHeaderAndNewlineInInclusiveByteLimit() {
        Capture a = one();
        Capture b = capture("0012ABFE",BitcoinAddressTest.B,a.capturedAt);
        int exact = ("uid,address,captured_at\n"+a.uid+","+a.address+","+a.capturedAt+"\n"+b.uid+","+b.address+","+b.capturedAt+"\n").getBytes(StandardCharsets.UTF_8).length;
        assertEquals(1,CsvExporter.export(Arrays.asList(a,b),DAY,5000,exact).size());
        assertEquals(1,CsvExporter.export(Arrays.asList(a,b),DAY,5000,exact+1).size());
        List<CsvExporter.Part> split = CsvExporter.export(Arrays.asList(a,b),DAY,5000,exact-1);
        assertEquals(2,split.size());
        assertEquals("uid,address,captured_at\n"+b.uid+","+b.address+","+b.capturedAt+"\n",text(split.get(1)));
        assertThrows(IllegalArgumentException.class, () -> CsvExporter.export(Arrays.asList(a),DAY,5000,23));
    }
    @Test public void lowerLimitSeamCannotBypassImporterCaps() {
        for (int[] limits : new int[][]{{0,1000},{-1,1000},{5001,1000},{5000,0},{5000,-1},{5000,1048577}})
            assertThrows(IllegalArgumentException.class, () -> CsvExporter.export(Arrays.asList(one()),DAY,limits[0],limits[1]));
        Capture b = capture("0012ABFE",BitcoinAddressTest.B,one().capturedAt);
        assertEquals(2,CsvExporter.export(Arrays.asList(one(),b),DAY,1,1048576).size());
    }
    @Test public void rejectsDuplicateBindingsInsteadOfWritingUnimportableParts() {
        Capture a = one();
        for (Capture b : new Capture[]{a,capture(a.uid,BitcoinAddressTest.B,a.capturedAt),capture("0012ABFE",a.address,a.capturedAt)})
            assertThrows(IllegalArgumentException.class, () -> CsvExporter.export(Arrays.asList(a,b),DAY,1,1048576));
    }
    static final String DAY = "2026-10-31";
    static Capture capture(String uid, String address, String time) { return new Capture(uid,address,time); }
    static Capture one() { return capture("0012ABFF",BitcoinAddressTest.A,DAY+"T23:59:59.123456Z"); }
    static String text(CsvExporter.Part p) { return new String(p.bytes,StandardCharsets.UTF_8); }
    @Test public void exportsExactHeaderUtf8AndUnchangedTimestamp() {
        List<CsvExporter.Part> parts = CsvExporter.export(Arrays.asList(one()), DAY);
        assertEquals(1,parts.size());
        assertEquals("2026-10-31-part-001.csv",parts.get(0).filename);
        assertEquals("uid,address,captured_at\n0012ABFF,"+BitcoinAddressTest.A+",2026-10-31T23:59:59.123456Z\n",text(parts.get(0)));
    }
}
