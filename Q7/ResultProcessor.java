import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Q7 - concurrent, fault-tolerant semester result processor.
 * Score file format (one student per file):
 *   line 1: register number (9 digits)
 *   line 2: five comma-separated marks, each 0..100
 */
public class ResultProcessor {

    /** Checked: a file that exists but cannot be turned into a result. */
    static class CorruptScoreFileException extends Exception {
        CorruptScoreFileException(String m) { super(m); }
    }

    record StudentResult(String regNo, int total, double gpa, boolean pass) { }
    record Failure(String file, String kind, String reason) { }
    record Summary(int files, int ok, int corrupt, int missing, int otherErrors, int passed, double avgGpa, long millis) {
        boolean complete() { return ok + corrupt + missing + otherErrors == files; }
    }

    /** The unit of work: read, validate, compute. Runs on a pool thread. */
    static final class ScoreTask implements Callable<StudentResult> {
        private final Path file;
        ScoreTask(Path file) { this.file = file; }
        @Override public StudentResult call() throws IOException, CorruptScoreFileException {
            try (BufferedReader r = Files.newBufferedReader(file)) {
                String reg = r.readLine(), marksLine = r.readLine();
                if (reg == null || marksLine == null) throw new CorruptScoreFileException("truncated file");
                reg = reg.trim();
                if (!reg.matches("\\d{9}")) throw new CorruptScoreFileException("bad register number '" + reg + "'");
                String[] parts = marksLine.split(",");
                if (parts.length != 5) throw new CorruptScoreFileException("expected 5 marks, found " + parts.length);
                int total = 0; double points = 0; boolean pass = true;
                for (String p : parts) {
                    int m;
                    try { m = Integer.parseInt(p.trim()); }
                    catch (NumberFormatException e) { throw new CorruptScoreFileException("non-numeric mark '" + p.trim() + "'"); }
                    if (m < 0 || m > 100) throw new CorruptScoreFileException("mark out of range " + m);
                    total += m;
                    points += gradePoint(m);
                    if (m < 50) pass = false;
                }
                return new StudentResult(reg, total, points / 5.0, pass);
            }
        }
        static int gradePoint(int m) { return m >= 90 ? 10 : m >= 80 ? 9 : m >= 70 ? 8 : m >= 60 ? 7 : m >= 50 ? 6 : 0; }
    }

    /** Processes every listed file with a fixed pool of p threads. A single bad file never stops the run. */
    static Summary process(List<Path> files, int p, Map<String, StudentResult> results, Queue<Failure> failures) throws InterruptedException {
        long t0 = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(p);
        List<Future<StudentResult>> futures = new ArrayList<>(files.size());
        try {
            for (Path f : files) futures.add(pool.submit(new ScoreTask(f)));
            for (int i = 0; i < futures.size(); i++) {
                String name = files.get(i).getFileName().toString();
                try {
                    StudentResult r = futures.get(i).get(30, TimeUnit.SECONDS);
                    if (results.putIfAbsent(r.regNo(), r) != null) failures.add(new Failure(name, "OTHER", "duplicate register number " + r.regNo()));
                } catch (ExecutionException e) {                       // the task threw: classify, record, continue
                    Throwable c = e.getCause();
                    String kind = c instanceof NoSuchFileException ? "MISSING" : c instanceof CorruptScoreFileException ? "CORRUPT" : "OTHER";
                    failures.add(new Failure(name, kind, c.toString()));
                } catch (TimeoutException e) {
                    futures.get(i).cancel(true);
                    failures.add(new Failure(name, "OTHER", "timed out"));
                }
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
        int corrupt = 0, missing = 0, other = 0;
        for (Failure f : failures) switch (f.kind()) { case "CORRUPT" -> corrupt++; case "MISSING" -> missing++; default -> other++; }
        int passed = 0; double gpa = 0;
        for (StudentResult r : results.values()) { gpa += r.gpa(); if (r.pass()) passed++; }
        return new Summary(files.size(), results.size(), corrupt, missing, other, passed,
                results.isEmpty() ? 0 : gpa / results.size(), (System.nanoTime() - t0) / 1_000_000);
    }

    static List<Path> readIndex(Path dir) throws IOException {
        List<Path> l = new ArrayList<>();
        for (String name : Files.readAllLines(dir.resolve("index.txt"))) if (!name.isBlank()) l.add(dir.resolve(name.trim()));
        return l;
    }
}
