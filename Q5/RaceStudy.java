import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Q5 - reliable race reproduction and comparison of synchronisation strategies. */
public class RaceStudy {

    interface Booking { boolean book(int seat); String name(); }

    /** Non-critical work done per booking (validate request + build receipt), about 1-2 us. */
    static String receipt(int seat) {
        StringBuilder sb = new StringBuilder("SCREEN-1 ROW ").append((char) ('A' + seat % 20)).append(" SEAT ").append(seat);
        for (int i = 0; i < 20; i++) sb.append(Integer.toHexString(sb.toString().hashCode()));
        return sb.toString();
    }

    static final class Unsafe implements Booking {
        final boolean[] booked; Unsafe(int s) { booked = new boolean[s]; }
        public boolean book(int seat) {
            if (!booked[seat]) {                                   // CHECK
                try { Thread.sleep(1); } catch (InterruptedException e) { }   // widen the window (payment gateway call)
                booked[seat] = true;                               // ACT
                return true;
            }
            return false;
        }
        public String name() { return "unsynchronized + 1 ms between check and act"; }
    }
    static final class UnsafeNoYield implements Booking {
        final boolean[] booked; UnsafeNoYield(int s) { booked = new boolean[s]; }
        public boolean book(int seat) { receipt(seat); if (!booked[seat]) { booked[seat] = true; return true; } return false; }
        public String name() { return "unsynchronized (no yield)"; }
    }
    static final class SyncMethod implements Booking {
        final boolean[] booked; SyncMethod(int s) { booked = new boolean[s]; }
        public synchronized boolean book(int seat) {                   // lock held for receipt() too
            receipt(seat);
            if (!booked[seat]) { booked[seat] = true; return true; }
            return false;
        }
        public String name() { return "synchronized method"; }
    }
    static final class SyncBlock implements Booking {
        final boolean[] booked; final Object lock = new Object(); SyncBlock(int s) { booked = new boolean[s]; }
        public boolean book(int seat) {
            String r = receipt(seat);                                   // outside the lock
            synchronized (lock) {                                       // only the check-then-act
                if (booked[seat]) return false;
                booked[seat] = true;
            }
            return r != null;
        }
        public String name() { return "synchronized block"; }
    }
    static final class Atomic implements Booking {
        final AtomicBoolean[] booked;
        Atomic(int s) { booked = new AtomicBoolean[s]; for (int i = 0; i < s; i++) booked[i] = new AtomicBoolean(); }
        public boolean book(int seat) { receipt(seat); return booked[seat].compareAndSet(false, true); }
        public String name() { return "AtomicBoolean CAS"; }
    }
    static final class Locked implements Booking {
        final boolean[] booked; final ReentrantLock lock = new ReentrantLock(); Locked(int s) { booked = new boolean[s]; }
        public boolean book(int seat) {
            receipt(seat);
            lock.lock();
            try { if (booked[seat]) return false; booked[seat] = true; return true; } finally { lock.unlock(); }
        }
        public String name() { return "ReentrantLock"; }
    }
    static final class Chm implements Booking {
        final ConcurrentHashMap<Integer, String> sold = new ConcurrentHashMap<>();
        public boolean book(int seat) { return sold.putIfAbsent(seat, receipt(seat)) == null; }
        public String name() { return "ConcurrentHashMap.putIfAbsent"; }
    }

    /** All threads wait at a latch and start together; returns {successful bookings, time ns}. */
    static long[] attack(Booking b, int seats, int threads, int attemptsPerThread) throws InterruptedException {
        AtomicInteger sold = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1), done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final int id = t;
            new Thread(() -> {
                try { go.await(); } catch (InterruptedException e) { return; }
                for (int k = 0; k < attemptsPerThread; k++) if (b.book((id + k * 7) % seats)) sold.incrementAndGet();
                done.countDown();
            }).start();
        }
        long t0 = System.nanoTime();
        go.countDown();
        done.await();
        return new long[]{sold.get(), System.nanoTime() - t0};
    }

    public static void main(String[] args) throws Exception {
        System.out.println("A) Reliable reproduction: 10 seats, 100 threads released together by a CountDownLatch, 10 runs");
        StringBuilder sb = new StringBuilder(); int dbl = 0;
        for (int run = 1; run <= 10; run++) { long sold = attack(new Unsafe(10), 10, 100, 1)[0]; dbl += sold - 10; sb.append(sold).append(run < 10 ? ", " : ""); }
        System.out.println("   unsync + 1 ms window     : seats sold per run = [" + sb + "]  double bookings in 10 runs = " + dbl);
        sb = new StringBuilder(); dbl = 0;
        for (int run = 1; run <= 10; run++) { long sold = attack(new UnsafeNoYield(100_000), 100_000, 8, 125_000)[0]; dbl += sold - 100_000; sb.append(sold - 100_000).append(run < 10 ? ", " : ""); }
        System.out.println("   unsync, no delay, 8 threads x 125,000 attempts on 100,000 seats: double bookings per run = [" + sb + "]  total = " + dbl);
        sb = new StringBuilder();
        for (int run = 1; run <= 10; run++) sb.append(attack(new SyncMethod(10), 10, 100, 1)[0]).append(run < 10 ? ", " : "");
        System.out.println("   synchronized method      : seats sold per run = [" + sb + "]");
        System.out.println();
        System.out.println("B) 10,000 bookings (1,000 seats, 8 threads x 1,250 attempts), mean of 5 runs after 1 warm-up");
        System.out.printf("   %-32s %12s %10s%n", "strategy", "time (ms)", "sold");
        int seats = 1_000;
        java.util.function.Supplier<?>[] makers = {
            () -> new UnsafeNoYield(seats), () -> new SyncMethod(seats), () -> new SyncBlock(seats),
            () -> new Atomic(seats), () -> new Locked(seats), () -> new Chm() };
        for (java.util.function.Supplier<?> m : makers) {
            double t = 0; long sold = 0; String name = "";
            for (int rep = 0; rep < 6; rep++) {
                Booking b = (Booking) m.get(); name = b.name();
                long[] r = attack(b, seats, 8, 1_250);
                if (rep > 0) { t += r[1]; sold = Math.max(sold, r[0]); }
            }
            System.out.printf("   %-32s %12.2f %10d%n", name, t / 5 / 1e6, sold);
        }
    }
}
