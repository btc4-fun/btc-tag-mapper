import com.saebm.btcnfcseller.core.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Disposable proof adapter: calls the actual production validator/exporter. */
public final class ExportProof {
    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8);
        if (args[0].equals("validate")) {
            List<String> verdicts = new ArrayList<>();
            for (String line : lines) verdicts.add(Boolean.toString(BitcoinAddress.isValid(line)));
            Files.write(Paths.get(args[2]), verdicts, StandardCharsets.UTF_8);
            return;
        }
        List<Capture> captures = new ArrayList<>();
        for (String line : lines) {
            String[] columns = line.split("\t", -1);
            captures.add(new Capture(columns[0], columns[1], columns[2]));
        }
        Path output = Paths.get(args[2]);
        Files.createDirectories(output);
        for (CsvExporter.Part part : CsvExporter.export(captures, "2026-10-31"))
            Files.write(output.resolve(part.filename), part.bytes);
    }
}
