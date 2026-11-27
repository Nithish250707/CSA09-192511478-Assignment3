import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Q6 Part A - hostel wallet transfers: deadlock, lock ordering, and tryLock with timeout. */
public class WalletTransfers {
    static final class Wallet {
        final int id; long balance; final ReentrantLock lock = new ReentrantLock();
        Wallet(int id, long balance) { this.id = id; this.balance = balance; }
    }

    /** Naive: lock 'from' then 'to'. Two opposite transfers deadlock. */
    static void transferNaive(Wallet from, Wallet to, long amt, boolean pause) {
        synchronized (from) {
            if (pause) sleep(50);                        // makes the deadlock reliable
            synchronized (to) { from.balance -= amt; to.balance += amt; }
        }
    }

    /** Fixed: always lock the wallet with the smaller id first (global lock order). */
    static void transferOrdered(Wallet from, Wallet to, long amt, boolean pause) {
        Wallet first = from.id < to.id ? from : to, second = from.id < to.id ? to : from;
        synchronized (first) {
            if (pause) sleep(50);
            synchronized (second) { from.balance -= amt; to.balance += amt; }
        }
    }

    /** Alternative: tryLock with timeout, back off and retry. Returns number of attempts. */
    static int transferTryLock(Wallet from, Wallet to, long amt) throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            if (from.lock.tryLock(1, TimeUnit.MILLISECONDS)) {
                try {
                    if (to.lock.tryLock(1, TimeUnit.MILLISECONDS)) {
                        try { from.balance -= amt; to.balance += amt; return attempt; } finally { to.lock.unlock(); }
                    }
                } finally { from.lock.unlock(); }
            }
            for (int i = 0; i < (attempt % 7) * 50; i++) Thread.onSpinWait();   // short, varying back-off
        }
    }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    public static void main(String[] args) throws Exception {
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        System.out.println("1) Naive transfers A->B (Rs 100) and B->A (Rs 50) at the same time, 5 trials");
        int deadlocks = 0;
        for (int trial = 1; trial <= 5; trial++) {
            Wallet a = new Wallet(1, 1000), b = new Wallet(2, 1000);
            Thread t1 = new Thread(() -> transferNaive(a, b, 100, true), "T1(A->B)");
            Thread t2 = new Thread(() -> transferNaive(b, a, 50, true), "T2(B->A)");
            t1.setDaemon(true); t2.setDaemon(true);          // so the JVM can still exit
            t1.start(); t2.start();
            t1.join(1000); t2.join(1000);
            long[] ids = mx.findMonitorDeadlockedThreads();
            if (ids != null) {
                deadlocks++;
                if (trial == 1) for (ThreadInfo ti : mx.getThreadInfo(ids))
                    System.out.println("   " + ti.getThreadName() + " is " + ti.getThreadState() + " waiting for " + ti.getLockName() + " held by " + ti.getLockOwnerName());
            }
            System.out.println("   trial " + trial + ": " + (ids != null ? "DEADLOCK detected (" + ids.length + " threads), balances never updated" : "completed"));
        }
        System.out.println("   deadlocked in " + deadlocks + "/5 trials");

        System.out.println();
        System.out.println("2) Same pair of transfers with lock ordering (lower wallet id first), 5 trials");
        for (int trial = 1; trial <= 5; trial++) {
            Wallet a = new Wallet(1, 1000), b = new Wallet(2, 1000);
            Thread t1 = new Thread(() -> transferOrdered(a, b, 100, true)), t2 = new Thread(() -> transferOrdered(b, a, 50, true));
            t1.start(); t2.start(); t1.join(2000); t2.join(2000);
            System.out.println("   trial " + trial + ": " + (t1.isAlive() || t2.isAlive() ? "STUCK" : "completed, A=" + a.balance + " B=" + b.balance + " (expected A=950 B=1050)"));
        }

        System.out.println();
        System.out.println("3) Stress: 8 threads, 10 wallets, 200,000 random transfers each strategy; money must be conserved");
        for (String kind : new String[]{"ordered", "tryLock"}) {
            Wallet[] w = new Wallet[10]; for (int i = 0; i < 10; i++) w[i] = new Wallet(i, 10_000);
            Thread[] ts = new Thread[8];
            long[] attempts = new long[8];
            long t0 = System.nanoTime();
            for (int t = 0; t < 8; t++) {
                final int id = t;
                ts[t] = new Thread(() -> {
                    java.util.Random r = new java.util.Random(id);
                    for (int k = 0; k < 25_000; k++) {
                        int x = r.nextInt(10), y = r.nextInt(10); if (x == y) y = (y + 1) % 10;
                        try {
                            if (kind.equals("ordered")) { transferOrdered(w[x], w[y], 1 + r.nextInt(20), false); attempts[id]++; }
                            else attempts[id] += transferTryLock(w[x], w[y], 1 + r.nextInt(20));
                        } catch (InterruptedException e) { return; }
                    }
                });
                ts[t].start();
            }
            for (Thread t : ts) t.join();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            long total = 0; for (Wallet x : w) total += x.balance;
            long att = 0; for (long a : attempts) att += a;
            System.out.printf("   %-8s: %4d ms, total money = %d (expected 100000), lock attempts per transfer = %.3f%n", kind, ms, total, att / 200_000.0);
        }
    }
}
