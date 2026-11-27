import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class Q7Main {

    /** Generates n score files; about 2% corrupt (4 kinds) and 1% missing (listed in index but not written). */
    static int[] generate(Path dir, int n) throws Exception {
        Files.createDirectories(dir);
        Random r = new Random(2026);
        List<String> index = new ArrayList<>();
        int corrupt = 0, missing = 0;
        for (int i = 0; i < n; i++) {
            String name = String.format("score_%05d.txt", i);
            index.add(name);
            double x = r.nextDouble();
            if (x < 0.01) { missing++; Files.deleteIfExists(dir.resolve(name)); continue; }
            String reg = String.valueOf(192500000 + i);
            StringBuilder marks = new StringBuilder();
            for (int s = 0; s < 5; s++) marks.append(s == 0 ? "" : ",").append(35 + r.nextInt(66));
            String content = reg + "\n" + marks + "\n";
            if (x < 0.03) {
                corrupt++;
                content = switch (corrupt % 4) {
                    case 0 -> reg + "\n" + "78,AB,90,66,71\n";       // non-numeric mark
                    case 1 -> reg + "\n" + "78,90,66\n";             // wrong number of marks
                    case 2 -> reg + "\n" + "78,190,66,71,80\n";      // out of range
                    default -> "";                                    // empty / truncated file
                };
            }
            Files.writeString(dir.resolve(name), content);
        }
        Files.write(dir.resolve("index.txt"), index);
        return new int[]{corrupt, missing};
    }

    public static void main(String[] args) throws Exception {
        // ---------- 1. hand-checked sample set ----------
        Path sample = Path.of("sample");
        Map<String, ResultProcessor.StudentResult> res = new ConcurrentHashMap<>();
        Queue<ResultProcessor.Failure> fail = new ConcurrentLinkedQueue<>();
        ResultProcessor.Summary s = ResultProcessor.process(ResultProcessor.readIndex(sample), 4, res, fail);
        System.out.println("1) Hand-checked sample (sample/index.txt, 10 entries), pool of 4");
        System.out.println("   " + s + "  complete=" + s.complete());
        res.values().stream().sorted((a, b) -> a.regNo().compareTo(b.regNo())).forEach(x -> System.out.println("   OK      " + x));
        fail.stream().sorted((a, b) -> a.file().compareTo(b.file())).forEach(f -> System.out.println("   " + String.format("%-7s", f.kind()) + " " + f.file() + " : " + f.reason()));

        // ---------- 2. 10,000 files, pools of 1, 2, 4, 8 (and 16) ----------
        Path data = Path.of("data");
        int[] injected = generate(data, 10_000);
        System.out.println();
        System.out.println("2) 10,000 generated files: injected corrupt = " + injected[0] + ", missing = " + injected[1]);
        List<Path> files = ResultProcessor.readIndex(data);
        ResultProcessor.process(files, 4, new ConcurrentHashMap<>(), new ConcurrentLinkedQueue<>());   // warm-up (JIT + OS file cache)
        System.out.printf("   %-6s %10s %10s %10s %8s %8s %8s %9s%n", "pool", "run1 ms", "run2 ms", "run3 ms", "ok", "corrupt", "missing", "complete");
        for (int p : new int[]{1, 2, 4, 8, 16}) {
            long[] ms = new long[3]; ResultProcessor.Summary last = null;
            for (int rep = 0; rep < 3; rep++) {
                last = ResultProcessor.process(files, p, new ConcurrentHashMap<>(), new ConcurrentLinkedQueue<>());
                ms[rep] = last.millis();
            }
            System.out.printf("   %-6d %10d %10d %10d %8d %8d %8d %9s%n", p, ms[0], ms[1], ms[2], last.ok(), last.corrupt(), last.missing(),
                    last.complete() && last.corrupt() == injected[0] && last.missing() == injected[1]);
        }

        // ---------- 3. alternatives: parallel stream and one thread per file ----------
        System.out.println();
        System.out.println("3) Alternatives on the same 10,000 files");
        for (int rep = 0; rep < 2; rep++) {
            AtomicInteger ok = new AtomicInteger(), bad = new AtomicInteger();
            long t0 = System.nanoTime();
            files.parallelStream().forEach(f -> {
                try { new ResultProcessor.ScoreTask(f).call(); ok.incrementAndGet(); }
                catch (Exception e) { bad.incrementAndGet(); }          // must be caught INSIDE the lambda
            });
            long t1 = System.nanoTime();
            if (rep == 1) System.out.printf("   parallelStream (common pool, %d workers): %d ms, ok=%d, failed=%d%n",
                    java.util.concurrent.ForkJoinPool.commonPool().getParallelism(), (t1 - t0) / 1_000_000, ok.get(), bad.get());
        }
        try {
            files.parallelStream().map(f -> { try { return new ResultProcessor.ScoreTask(f).call(); } catch (Exception e) { throw new RuntimeException(e); } }).toList();
        } catch (RuntimeException e) {
            Throwable root = e; while (root.getCause() != null) root = root.getCause();
            System.out.println("   parallelStream without per-element catch: whole pipeline aborted by " + root.getClass().getSimpleName() + ": " + root.getMessage());
        }
        AtomicInteger ok = new AtomicInteger(), bad = new AtomicInteger(), live = new AtomicInteger(), peak = new AtomicInteger();
        long t0 = System.nanoTime();
        List<Thread> threads = new ArrayList<>(files.size());
        for (Path f : files) {
            Thread t = new Thread(() -> {
                int now = live.incrementAndGet(); peak.accumulateAndGet(now, Math::max);
                try { new ResultProcessor.ScoreTask(f).call(); ok.incrementAndGet(); } catch (Exception e) { bad.incrementAndGet(); }
                finally { live.decrementAndGet(); }
            });
            threads.add(t); t.start();
        }
        for (Thread t : threads) t.join();
        System.out.printf("   one thread per file: %d ms, ok=%d, failed=%d, threads created=%d, peak alive at once=%d%n",
                (System.nanoTime() - t0) / 1_000_000, ok.get(), bad.get(), threads.size(), peak.get());
    
        // ---------- 4. where does the time go? ----------
        System.out.println();
        System.out.println("4) Breakdown, single thread: open+read only vs parse+validate only");
        List<byte[]> contents = new ArrayList<>();
        long r0 = System.nanoTime();
        for (Path f : files) { try { contents.add(Files.readAllBytes(f)); } catch (Exception e) { contents.add(new byte[0]); } }
        long r1 = System.nanoTime();
        long sink = 0;
        for (byte[] b : contents) { String t = new String(b); String[] lines = t.split("\n"); if (lines.length == 2) for (String m : lines[1].split(",")) { try { sink += Integer.parseInt(m.trim()); } catch (NumberFormatException e) { } } }
        long r2 = System.nanoTime();
        System.out.printf("   open+read 10,000 files: %d ms | parse+validate in memory: %.1f ms%n", (r1 - r0) / 1_000_000, (r2 - r1) / 1e6);
        for (int p : new int[]{1, 2, 4, 8}) {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(p);
            long t2 = System.nanoTime();
            List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
            for (Path f : files) fs.add(pool.submit(() -> { try { Files.readAllBytes(f); } catch (Exception e) { } }));
            for (java.util.concurrent.Future<?> fu : fs) fu.get();
            pool.shutdown();
            System.out.printf("   read-only with pool p=%d: %d ms%n", p, (System.nanoTime() - t2) / 1_000_000);
        }

        // ---------- 5. same design with CPU-heavy validation (checksum of each file x 300) ----------
        System.out.println();
        System.out.println("5) Same ExecutorService design when each file needs CPU work (5,000 x SHA-256 per file)");
        for (int p : new int[]{1, 2, 4, 8, 16}) {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(p);
            long t3 = System.nanoTime();
            List<java.util.concurrent.Future<Integer>> fs = new ArrayList<>();
            for (Path f : files) fs.add(pool.submit(() -> {
                byte[] b = Files.exists(f) ? Files.readAllBytes(f) : new byte[0];
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                for (int k = 0; k < 5_000; k++) b = md.digest(b);
                return (int) b[0];
            }));
            long acc = 0; for (java.util.concurrent.Future<Integer> fu : fs) acc += fu.get();
            pool.shutdown();
            System.out.printf("   p=%-3d %6d ms%n", p, (System.nanoTime() - t3) / 1_000_000);
            if (acc == Long.MIN_VALUE) System.out.println();
        }
        if (sink == -1) System.out.println();
    }
}
