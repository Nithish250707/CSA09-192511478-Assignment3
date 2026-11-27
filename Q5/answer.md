# Q5 – Race Conditions in a Cinema Seat-Booking System (K4)

> Pair-work note: Student A and Student B parts were both done by me (Nithish G).

## Complete the Code – `SeatBooking.java`

**Run 1, as instructed: TODO 1 written WITHOUT `synchronized`** (`unsync/SeatBooking.java`):

```java
public void book(int seat) {
    if (!booked[seat]) {
        booked[seat] = true;
        bookingsMade++;
    }
}
```

**Run 2, TODO 1 completed correctly** (`SeatBooking.java`; the only difference is the keyword):

```java
public synchronized void book(int seat) { ...same body... }
```

| Version | 10 runs of the given `main()` |
|---|---|
| Without `synchronized` | 10, 10, 10, 10, 10, 10, 10, 10, 10, 10 |
| With `synchronized` | 10, 10, 10, 10, 10, 10, 10, 10, 10, 10 (**expected output `Seats sold: 10`** ✔) |

**Honest observation:** the given `main()` did *not* show the bug in 10 runs. Each user thread is started one after another with `start()`, which takes tens of microseconds, while `book()` runs in nanoseconds. Each thread usually finishes before the next one starts, so the threads rarely overlap inside `book()`. The program is still **incorrect**: a passing test only shows that the race did not happen in those runs. The experiments below make it happen on purpose.

**Why the unsynchronized version may sell more than 10 seats:**

* `if (!booked[seat]) { booked[seat] = true; bookingsMade++; }` is a *check-then-act* sequence with no lock. Two threads can both read `false` before either writes `true`, so both "sell" the seat and `bookingsMade` is incremented twice.
* `bookingsMade++` is itself three steps (read, add 1, write). Two threads can lose an update, so the counter can even be *lower* than the number of seats actually marked sold.
* Without synchronisation there is also no visibility guarantee: one thread's write to `booked[seat]` may not yet be visible to another.

## Shared group work

`RaceStudy.java` implements the unsynchronized booking, a synchronized **method** and a synchronized **block** (plus three alternatives), and attacks each with many threads released at the same instant by a `CountDownLatch`. Each version counts how many threads *believed* they sold a seat.

**The synchronized versions never double-booked:**

* the method version sold exactly 10 seats in all 10 runs of test A;
* the method, block, CAS, lock and map versions all sold exactly 1,000 of 1,000 seats in test B;
* the unsynchronized version sold 1,024 seats.

## Student A – Producing and Explaining the Race

### A test that reliably reproduces double booking

1. **Widen the window:** 10 seats and 100 threads, all released together by a `CountDownLatch`. The unsafe `book` sleeps 1 ms between the check and the act, simulating a payment-gateway call between "seat free?" and "mark sold".
2. **No artificial delay, but high contention:** 8 threads hammer 100,000 seats with 125,000 attempts each.

### Double bookings over repeated runs (real output)

| Test | Seats sold per run (10 runs) | Double bookings |
|---|---|---|
| 1. unsync + 1 ms window | 100, 100, 100, 100, 100, 100, 100, 100, 100, 100 | **900** (every seat sold 10 times, in **every** run) |
| 2. unsync, no delay | double bookings per run: 1, 6, 0, 60, 254, 1570, 391, 976, 354, 995 | **4,607**, in 9 of 10 runs |
| synchronized method (same as test 1, 100 threads) | 10 in every run | **0** |

Test 1 is fully reliable (10/10). Test 2 shows the race also happens *without* any artificial delay, just less predictably.

### The race step by step (two threads, seat 3)

| Step | Thread T1 (Arun) | Thread T2 (Divya) | `booked[3]` | `bookingsMade` |
|---|---|---|---|---|
| 1 | reads `booked[3]` → `false` | | false | 0 |
| 2 | *(pre-empted / still in the window)* | reads `booked[3]` → `false` | false | 0 |
| 3 | writes `booked[3] = true` | | true | 0 |
| 4 | reads `bookingsMade` (0) | | true | 0 |
| 5 | | writes `booked[3] = true` (no change) | true | 0 |
| 6 | | reads `bookingsMade` (0) | true | 0 |
| 7 | writes `bookingsMade = 1` | | true | 1 |
| 8 | | writes `bookingsMade = 1` (lost update) | true | 1 |

Result: **both** customers are told "seat 3 is yours", so the seat is sold twice. In this interleaving the counter even says 1, so the double sale is invisible in the totals. With other interleavings the counter reads 2.

### The exact critical section

```java
if (!booked[seat]) {        // ┐
    booked[seat] = true;    // │  critical section: read booked[seat], decide, write booked[seat],
    bookingsMade++;         // │  update the shared counter – must execute as ONE indivisible step
}                           // ┘
```

Shared mutable state: `booked[seat]` and `bookingsMade`. Validating the seat number, computing the price and building the receipt are **not** part of it.

## Student B – Comparing Synchronization Strategies

### Time to process 10,000 bookings

Setup: 1,000 seats, 8 threads × 1,250 attempts. Each booking also does ~1–2 µs of non-critical work (building a receipt). Mean of 5 runs after 1 warm-up.

| Strategy | Time (ms) | Seats sold | Correct? |
|---|---|---|---|
| unsynchronized | 3.18 | **1,024** | ✘ 24 double bookings |
| `synchronized` **method** (lock held during receipt building too) | **22.73** | 1,000 | ✔ |
| `synchronized` **block** (lock only around check-then-act) | 3.30 | 1,000 | ✔ |
| `AtomicBoolean[]` + `compareAndSet` | 3.19 | 1,000 | ✔ |
| `ReentrantLock` (critical section only) | 3.34 | 1,000 | ✔ |
| `ConcurrentHashMap.putIfAbsent` | 3.82 | 1,000 | ✔ |

### Locking the whole method vs only the critical section

* The **synchronized method** was **6.9× slower** than the block (22.7 vs 3.3 ms). Its lock covers the receipt work, so the 8 threads take turns doing *all* their work: effectively single-threaded.
* The **block** holds the lock only for the few nanoseconds of check-then-act, so the receipt work runs in parallel and the block is as fast as the *unsafe* version, yet correct.
* Rule: keep the critical section as small as possible, but never smaller than the full check-then-act.

### Static synchronization, and when it would be needed here

* A `static synchronized` method locks the **`Class` object** (`SeatBooking.class`), one lock for all instances, not `this`.
* It is needed when the shared state is **static**. Example: a *cinema-wide* `static int totalTicketsSoldToday` or a static receipt-number counter updated by all 20 screens. Instance locks would let two screens update it at the same time.
* It must not be used for per-screen seat arrays: it would make all 20 screens wait for each other for no reason.

### The intrinsic lock each version uses

| Version | Lock (monitor) acquired |
|---|---|
| `synchronized` instance method | the intrinsic lock of `this` (the `SeatBooking` object for that screen) |
| `synchronized (lock) { … }` block | the intrinsic lock of the private `lock` object (could also be `this`); a private lock object cannot be grabbed by outside code |
| `static synchronized` | the intrinsic lock of `SeatBooking.class` |
| `ReentrantLock` | not intrinsic: an explicit `java.util.concurrent` lock object (supports `tryLock`, fairness, timeouts) |
| `AtomicBoolean.compareAndSet` | **no lock**: a single CPU compare-and-swap instruction |

Intrinsic locks are *reentrant* and give happens-before visibility: everything written before `unlock` is visible to the next thread that acquires the same lock.

## Joint engineering decision – a cinema with 20 screens

**Recommended:** one lock **per screen** with a **minimal critical section**, or better, lock-free `compareAndSet` per seat.

* Each screen object owns its own seats and its own lock (a `synchronized` block on a private lock, or `AtomicBoolean[]` seats). Bookings on different screens then never contend: 20 screens means 20 independent locks.
* Inside a screen, only check-then-act is locked. Payment, receipt and email happen outside the lock. Measured: a block or CAS is 7× faster than a whole-method lock under contention.
* For **multi-seat bookings** (a family of 4 must get all 4 seats or none), per-seat CAS is not enough. Use the per-screen lock (block or `ReentrantLock`) around "check all 4 free → book all 4".
* Cinema-wide totals use an `AtomicLong` (or `LongAdder`), not a global lock.
* With several servers, the decision moves to the database (a unique constraint on `(screen, show, seat)` or `SELECT … FOR UPDATE`), because JVM locks do not span machines.

## Alternative Concept and Complexity Analysis

```java
// 1. AtomicBoolean[] with CAS
boolean book(int seat) { return booked[seat].compareAndSet(false, true); }   // true for exactly one winner

// 2. ReentrantLock
lock.lock(); try { if (booked[seat]) return false; booked[seat] = true; return true; } finally { lock.unlock(); }

// 3. ConcurrentHashMap
boolean book(int seat) { return sold.putIfAbsent(seat, customerId) == null; }   // atomic insert-if-absent
```

For s seats and c threads contending:

| Approach | Time per booking (uncontended) | Under contention | Extra space for s seats | Measured (10k bookings) |
|---|---|---|---|---|
| `synchronized` method/block | O(1) | threads on the *same screen* serialise; waiting time grows O(c) | 0 extra (monitor in the object header) + `boolean[s]` = s bytes | 22.7 ms method / 3.3 ms block |
| `AtomicBoolean[]` CAS | O(1), no lock | O(1) per attempt. A losing CAS simply returns `false` (seat taken), so no retry loop is needed; contention only on the *same* seat | s `AtomicBoolean` objects ≈ 16 B each + references, ≈ **20 s bytes** (vs s bytes for `boolean[]`); `AtomicIntegerArray` would reduce this to 4 s bytes | 3.19 ms |
| `ReentrantLock` | O(1) | like `synchronized`, with an optional fair queue (FIFO, lower throughput) and `tryLock(timeout)` | one lock object (~32 B) + an O(c) wait queue + `boolean[s]` | 3.34 ms |
| `ConcurrentHashMap.putIfAbsent` | O(1) average | bins are locked individually, so different seats almost never contend | O(sold) entries, ≈ 40–50 B per sold seat (node + boxed key + value) – only seats actually sold use memory | 3.82 ms |

All give O(1) per booking. The real differences are the **granularity of contention** (whole method > block ≈ lock > map bin > single-seat CAS) and **memory per seat**. For a fixed seat map, `AtomicBoolean[]` (or `AtomicIntegerArray`) is the simplest correct and fastest choice. `ConcurrentHashMap` fits sparse or dynamic seat IDs and stores *who* booked each seat.
