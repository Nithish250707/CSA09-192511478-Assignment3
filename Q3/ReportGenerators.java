import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/** Q3 - daily sales report: manual close() version (leaks) and try-with-resources version. */
public class ReportGenerators {
    static final Logger LOG = Logger.getLogger("sales");
    static String lastError = "";

    /** Number of file descriptors currently open in this JVM (macOS/Linux: entries of /dev/fd). */
    static int openFds() { String[] l = new File("/dev/fd").list(); return l == null ? -1 : l.length; }

    /** ORIGINAL: try-catch-finally with manual close(). */
    static boolean manual(List<String> stores, String outFile) {
        BufferedWriter out = null;
        String current = null;
        try {
            out = new BufferedWriter(new FileWriter(outFile));
            double grand = 0;
            for (String path : stores) {
                current = path;
                BufferedReader in = new BufferedReader(new FileReader(path));
                in.readLine();                                         // header
                double total = 0;
                String line;
                while ((line = in.readLine()) != null) {
                    String[] p = line.split(",");
                    total += Double.parseDouble(p[1]);                 // NumberFormatException on a bad line
                }
                in.close();                                            // NEVER reached if the loop throws -> leak
                out.write(new File(path).getName() + "," + String.format("%.2f", total));
                out.newLine();
                grand += total;
            }
            out.write("TOTAL," + String.format("%.2f", grand));
            out.newLine();
            return true;
        } catch (IOException | NumberFormatException e) {
            lastError = e.toString();
            LOG.severe("manual: failed while processing " + current + ": " + e);
            return false;
        } finally {
            try { if (out != null) out.close(); } catch (IOException e) { LOG.warning("could not close report: " + e); }
        }
    }

    /** IMPROVED: try-with-resources for every reader and the writer. */
    static boolean withResources(List<String> stores, String outFile) {
        String current = null;
        try (BufferedWriter out = new BufferedWriter(new FileWriter(outFile))) {
            double grand = 0;
            for (String path : stores) {
                current = path;
                double total = 0;
                try (BufferedReader in = new BufferedReader(new FileReader(path))) {
                    in.readLine();
                    String line;
                    while ((line = in.readLine()) != null) total += Double.parseDouble(line.split(",")[1]);
                }                                                      // closed here even if parseDouble throws
                out.write(new File(path).getName() + "," + String.format("%.2f", total));
                out.newLine();
                grand += total;
            }
            out.write("TOTAL," + String.format("%.2f", grand));
            out.newLine();
            return true;
        } catch (IOException | NumberFormatException e) {
            LOG.severe("try-with-resources: failed while processing " + current + ": " + e);
            return false;
        }
    }
}
