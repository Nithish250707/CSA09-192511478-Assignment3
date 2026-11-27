import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Q4 - four answer-script scanner stations as threads. */
public class ScannerStations {
    static final List<String> finishOrder = Collections.synchronizedList(new ArrayList<>());

    /** Way 1: extend Thread. */
    static class StationThread extends Thread {
        private final int scripts; private final long seed; int scanned;
        StationThread(String name, int scripts, long seed) { super(name); this.scripts = scripts; this.seed = seed; }
        @Override public void run() {
            Random r = new Random(seed);
            for (int i = 0; i < scripts; i++) {
                try { Thread.sleep(2 + r.nextInt(4)); } catch (InterruptedException e) { return; }   // TIMED_WAITING
                scanned++;
            }
            finishOrder.add(getName());
        }
    }

    /** Way 2: implement Runnable (the task is separate from the thread that runs it). */
    static class StationTask implements Runnable {
        private final int scripts; private final long seed; volatile int scanned;
        StationTask(int scripts, long seed) { this.scripts = scripts; this.seed = seed; }
        @Override public void run() {
            Random r = new Random(seed);
            for (int i = 0; i < scripts; i++) {
                try { Thread.sleep(2 + r.nextInt(4)); } catch (InterruptedException e) { return; }
                scanned++;
            }
            finishOrder.add(Thread.currentThread().getName());
        }
    }

    /** CPU-bound variant used for the priority experiment (sleep would hide priorities completely). */
    static class BusyStation extends Thread {
        BusyStation(String n) { super(n); }
        @Override public void run() { double x = 0; for (int i = 0; i < 40_000_000; i++) x += Math.sqrt(i); if (x > 0) finishOrder.add(getName()); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("daemon")) { daemonDemo(); return; }

        System.out.println("=== A. extends Thread, with start/sleep/join/isAlive and states ===");
        StationThread[] st = new StationThread[4];
        for (int i = 0; i < 4; i++) st[i] = new StationThread("Station-" + (i + 1), 25, i);
        System.out.println("before start: " + st[0].getName() + " state=" + st[0].getState() + " isAlive=" + st[0].isAlive());
        for (StationThread s : st) s.start();
        Thread.sleep(20);
        System.out.println("20 ms after start: state=" + st[0].getState() + " isAlive=" + st[0].isAlive());
        for (StationThread s : st) s.join();                      // main waits for each station
        int total = 0; for (StationThread s : st) total += s.scanned;
        System.out.println("after join: state=" + st[0].getState() + " isAlive=" + st[0].isAlive());
        System.out.println("finish order: " + finishOrder + "  TOTAL scanned = " + total);

        System.out.println();
        System.out.println("=== B. implements Runnable ===");
        finishOrder.clear();
        StationTask[] tasks = new StationTask[4]; Thread[] ts = new Thread[4];
        for (int i = 0; i < 4; i++) { tasks[i] = new StationTask(25, 10 + i); ts[i] = new Thread(tasks[i], "Station-" + (i + 1)); ts[i].start(); }
        for (Thread t : ts) t.join();
        total = 0; for (StationTask t : tasks) total += t.scanned;
        System.out.println("finish order: " + finishOrder + "  TOTAL scanned = " + total);

        System.out.println();
        System.out.println("=== C. run() instead of start() ===");
        Thread direct = new Thread(() -> System.out.println("  task executing in thread: " + Thread.currentThread().getName()), "Station-X");
        direct.run();
        System.out.println("  after run(): Station-X state=" + direct.getState() + " (never started)");
        direct.start(); direct.join();

        System.out.println();
        System.out.println("=== D. 10 runs: order in which stations finish (same seeds every run) ===");
        for (int run = 1; run <= 10; run++) {
            finishOrder.clear();
            StationThread[] s = new StationThread[4];
            for (int i = 0; i < 4; i++) s[i] = new StationThread("S" + (i + 1), 25, i);
            for (StationThread x : s) x.start();
            for (StationThread x : s) x.join();
            System.out.println("  run " + String.format("%2d", run) + ": " + finishOrder);
        }

        System.out.println();
        System.out.println("=== E. priorities (CPU-bound stations; S1=MAX_PRIORITY, others MIN_PRIORITY), 10 runs, "
                + Runtime.getRuntime().availableProcessors() + " cores ===");
        for (int run = 1; run <= 10; run++) {
            finishOrder.clear();
            BusyStation[] b = new BusyStation[4];
            for (int i = 0; i < 4; i++) { b[i] = new BusyStation("S" + (i + 1)); b[i].setPriority(i == 0 ? Thread.MAX_PRIORITY : Thread.MIN_PRIORITY); }
            for (BusyStation x : b) x.start();
            for (BusyStation x : b) x.join();
            System.out.println("  run " + String.format("%2d", run) + ": " + finishOrder);
        }

        System.out.println();
        System.out.println("=== F. ExecutorService alternative: n scripts (5 ms each), p threads ===");
        int n = 400;
        System.out.printf("  %-30s %10s%n", "approach", "time (ms)");
        long t0 = System.nanoTime();
        Thread[] manual = new Thread[4];
        for (int i = 0; i < 4; i++) { final int id = i; manual[i] = new Thread(() -> { for (int k = id; k < n; k += 4) sleep(5); }); manual[i].start(); }
        for (Thread t : manual) t.join();
        System.out.printf("  %-30s %10.0f   (expected ~ n/p x 5 = %d)%n", "4 manual Threads", (System.nanoTime() - t0) / 1e6, n / 4 * 5);
        for (int p : new int[]{1, 2, 4, 8, 16}) {
            ExecutorService pool = Executors.newFixedThreadPool(p);
            long s0 = System.nanoTime();
            List<Future<?>> fs = new ArrayList<>();
            for (int k = 0; k < n; k++) fs.add(pool.submit(() -> sleep(5)));
            for (Future<?> f : fs) f.get();
            pool.shutdown(); pool.awaitTermination(1, TimeUnit.MINUTES);
            System.out.printf("  %-30s %10.0f   (expected ~ n/p x 5 = %d)%n", "fixed pool p=" + p, (System.nanoTime() - s0) / 1e6, n / p * 5);
        }
    }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    static void daemonDemo() throws Exception {
        StationThread normal = new StationThread("Station-1(user)", 10, 1);
        StationThread daemon = new StationThread("Station-4(daemon)", 500, 4) {
            @Override public void run() { super.run(); System.out.println("  daemon station finished all 500 scripts"); }
        };
        daemon.setDaemon(true);
        normal.start(); daemon.start();
        normal.join();
        System.out.println("  main ends now; daemon has scanned " + daemon.scanned + " of 500, isAlive=" + daemon.isAlive());
    }
}
