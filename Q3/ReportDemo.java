import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ReportDemo {
    static List<String> stores(String dir) { List<String> l = new ArrayList<>(); for (int i = 1; i <= 5; i++) l.add(dir + "/store" + i + ".csv"); return l; }

    /** A resource whose close() itself fails - to show suppressed exceptions. */
    static final class FlakyResource implements AutoCloseable {
        final String name; FlakyResource(String n) { name = n; System.out.println("  open " + n); }
        @Override public void close() throws Exception { System.out.println("  close " + name); throw new IllegalStateException("close failed: " + name); }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.logging.SimpleFormatter.format", "  [LOG %4$s] %5$s%n");
        System.out.println("1) Good input - both versions");
        int fd0 = ReportGenerators.openFds();
        boolean a = ReportGenerators.manual(stores("stores"), "out/report_manual.csv");
        boolean b = ReportGenerators.withResources(stores("stores"), "out/report_twr.csv");
        System.out.println("  manual ok=" + a + ", twr ok=" + b + ", reports identical: "
                + Files.readString(Path.of("out/report_manual.csv")).equals(Files.readString(Path.of("out/report_twr.csv"))));
        System.out.print(Files.readString(Path.of("out/report_twr.csv")).replaceAll("(?m)^", "    "));
        System.out.println("  open fds before=" + fd0 + " after=" + ReportGenerators.openFds());

        System.out.println();
        System.out.println("2) Failing input (stores_bad/store3.csv has 'TWENTY' as an amount) - files left open");
        int before = ReportGenerators.openFds();
        ReportGenerators.manual(stores("stores_bad"), "out/bad_manual.csv");
        int afterManual = ReportGenerators.openFds();
        ReportGenerators.withResources(stores("stores_bad"), "out/bad_twr.csv");
        int afterTwr = ReportGenerators.openFds();
        System.out.println("  open fds: before=" + before + ", after manual=" + afterManual + " (leaked " + (afterManual - before)
                + "), after try-with-resources=" + afterTwr + " (leaked " + (afterTwr - afterManual) + ")");
        System.out.println("  partial report written by manual version:");
        System.out.print(Files.readString(Path.of("out/bad_manual.csv")).replaceAll("(?m)^", "    "));

        System.out.println();
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        if (runs > 0) {
            System.out.println("3) Repeating the failing manual run " + runs + " times (a server that re-runs the report)");
            ReportGenerators.LOG.setLevel(java.util.logging.Level.OFF);      // keep the output short
            for (int i = 1; i <= runs; i++) {
                ReportGenerators.manual(stores("stores_bad"), "out/bad_manual.csv");
                if (i % 10 == 0) System.out.println("  after run " + i + ": open fds = " + ReportGenerators.openFds());
                if (ReportGenerators.lastError.contains("Too many open files")) {
                    System.out.println("  run " + i + " FAILED: " + ReportGenerators.lastError + "  (open fds = " + ReportGenerators.openFds() + ")");
                    break;
                }
            }
            ReportGenerators.LOG.setLevel(java.util.logging.Level.INFO);
        }
        System.out.println();
        System.out.println("4) Closing order and suppressed exceptions in try-with-resources");
        try (FlakyResource r1 = new FlakyResource("storeReader"); FlakyResource r2 = new FlakyResource("reportWriter")) {
            System.out.println("  body throws");
            throw new java.io.IOException("read failed in body");
        } catch (Exception e) {
            System.out.println("  caught primary: " + e);
            for (Throwable s : e.getSuppressed()) System.out.println("  suppressed   : " + s);
        }
    }
}
