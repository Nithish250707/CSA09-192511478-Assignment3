import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** Q6 Part B - instrumented counter (size over time), notify vs notifyAll, and ArrayBlockingQueue. */
public class ProducerConsumerStudy {

    static final class Counter {
        private final int[] buf; private int count, in, out;
        final boolean useNotifyAll;
        final List<Integer> sizes = new ArrayList<>();   // counter size after every put/take
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        Counter(int cap, boolean useNotifyAll) { buf = new int[cap]; this.useNotifyAll = useNotifyAll; }
        private void record() { sizes.add(count); min = Math.min(min, count); max = Math.max(max, count); }
        synchronized void put(int t) throws InterruptedException {
            while (count == buf.length) wait();
            buf[in] = t; in = (in + 1) % buf.length; count++; record();
            if (useNotifyAll) notifyAll(); else notify();
        }
        synchronized int take() throws InterruptedException {
            while (count == 0) wait();
            int t = buf[out]; out = (out + 1) % buf.length; count--; record();
            if (useNotifyAll) notifyAll(); else notify();
            return t;
        }
    }

    /** Runs producers/consumers; returns true if everything finished within the timeout. */
    static boolean run(Counter c, int producers, int consumers, int tokensEach, long timeoutMs, long[] sumOut) throws InterruptedException {
        List<Thread> all = new ArrayList<>();
        long[] sums = new long[consumers];
        int perConsumer = producers * tokensEach / consumers;
        for (int p = 0; p < producers; p++) {
            final int base = p * tokensEach;
            Thread t = new Thread(() -> { try { for (int k = 1; k <= tokensEach; k++) c.put(base + k); } catch (InterruptedException e) { } });
            t.setDaemon(true); all.add(t);
        }
        for (int s = 0; s < consumers; s++) {
            final int id = s;
            Thread t = new Thread(() -> { try { for (int k = 0; k < perConsumer; k++) sums[id] += c.take(); } catch (InterruptedException e) { } });
            t.setDaemon(true); all.add(t);
        }
        for (Thread t : all) t.start();
        long deadline = System.currentTimeMillis() + timeoutMs;
        for (Thread t : all) t.join(Math.max(1, deadline - System.currentTimeMillis()));
        boolean finished = all.stream().noneMatch(Thread::isAlive);
        long s = 0; for (long x : sums) s += x; sumOut[0] = s;
        return finished;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1) Counter size over time (capacity 5, 1 cook x 20 tokens, 2 students), notifyAll + while");
        Counter c = new Counter(5, true);
        long[] sum = new long[1];
        run(c, 1, 2, 20, 5000, sum);
        System.out.println("   sizes after each operation: " + c.sizes);
        System.out.println("   min = " + c.min + ", max = " + c.max + ", operations = " + c.sizes.size() + ", sum = " + sum[0] + " (expected 210)");
        Counter big = new Counter(5, true);
        run(big, 3, 4, 20_000, 20_000, sum);
        System.out.println("   stress 3 cooks x 20,000 tokens, 4 students: min = " + big.min + ", max = " + big.max + ", operations = " + big.sizes.size()
                + ", sum ok = " + (sum[0] == 3L * 20_000 * (3L * 20_000 + 1) / 2));

        System.out.println();
        System.out.println("2) notify() vs notifyAll(): 2 cooks + 2 students, capacity 1, 2,000 tokens per cook, 30 trials, 2 s timeout each");
        for (boolean all : new boolean[]{false, true}) {
            int hung = 0;
            for (int trial = 0; trial < 30; trial++) if (!run(new Counter(1, all), 2, 2, 2_000, 2_000, sum)) hung++;
            System.out.println("   " + (all ? "notifyAll()" : "notify()   ") + ": hung (all threads waiting) in " + hung + "/30 trials");
        }

        System.out.println();
        System.out.println("3) Time per put/take: hand-written wait/notifyAll counter vs ArrayBlockingQueue (capacity 5, 2 producers x 500,000, 2 consumers)");
        for (int rep = 0; rep < 3; rep++) {
            long t0 = System.nanoTime();
            runNoRecord(2, 2, 500_000, false);
            long t1 = System.nanoTime();
            runNoRecord(2, 2, 500_000, true);
            long t2 = System.nanoTime();
            if (rep > 0) System.out.printf("   round %d: wait/notifyAll %.0f ns per token | ArrayBlockingQueue %.0f ns per token%n", rep, (t1 - t0) / 1e6, (t2 - t1) / 1e6);
        }
    }

    /** Same workload without recording sizes. */
    static void runNoRecord(int producers, int consumers, int each, boolean abq) throws InterruptedException {
        BlockingQueue<Integer> q = new ArrayBlockingQueue<>(5);
        final int[] buf = new int[5]; final int[] st = new int[3]; final Object lock = new Object();
        List<Thread> ts = new ArrayList<>();
        int per = producers * each / consumers;
        for (int p = 0; p < producers; p++) ts.add(new Thread(() -> {
            try { for (int k = 0; k < each; k++) {
                if (abq) q.put(k);
                else synchronized (lock) { while (st[0] == 5) lock.wait(); buf[st[1]] = k; st[1] = (st[1] + 1) % 5; st[0]++; lock.notifyAll(); }
            } } catch (InterruptedException e) { }
        }));
        for (int s = 0; s < consumers; s++) ts.add(new Thread(() -> {
            try { for (int k = 0; k < per; k++) {
                if (abq) q.take();
                else synchronized (lock) { while (st[0] == 0) lock.wait(); st[2] = (st[2] + 1) % 5; st[0]--; lock.notifyAll(); }
            } } catch (InterruptedException e) { }
        }));
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
    }
}
