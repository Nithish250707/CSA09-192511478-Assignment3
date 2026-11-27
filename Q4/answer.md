# Q4 – Thread Creation and the Thread Life Cycle (K4)

> Pair-work note: Student A and Student B parts were both done by me (Nithish G).
> Machine: 10-core Apple Silicon Mac, JDK 21. Default thread stack: `ThreadStackSize = 2048` KB (from `-XX:+PrintFlagsFinal`).

## Shared group work (`ScannerStations.java`)

Each station scans 25 scripts, sleeping 2–5 ms per script (simulated scanning I/O).

* **A – `extends Thread`:** `StationThread`.
* **B – `implements Runnable`:** `StationTask`, wrapped in `new Thread(task, name)`.
* `start()`, `sleep()`, `join()` and `isAlive()` are all used.
* Real output for A: `TOTAL scanned = 100`; the finish order was `[Station-2, Station-1, Station-4, Station-3]`. B gave `TOTAL scanned = 100` as well.

## Student A – Thread Design

### Two ways to create a thread

| | `extends Thread` | `implements Runnable` |
|---|---|---|
| How | override `run()` in a `Thread` subclass; `new StationThread(...).start()` | put the work in `run()` of a task class; `new Thread(task, name).start()` |
| Inheritance | uses up the single `extends`; a station can no longer extend e.g. `Scanner` | the task can extend any class and implement other interfaces |
| Separation | the *work* and the *worker* are the same object | the work (task) is separate from the worker (thread), so the same task type can run on a thread, a thread pool or a virtual thread |
| Sharing | each thread object has its own fields | one `Runnable` instance can be given to several threads if they should share state (with synchronisation) |
| When preferred | quick demos; when thread-specific behaviour is customised (rare) | **almost always**, especially with `ExecutorService` (which accepts `Runnable`/`Callable`, not `Thread` subclasses) |

### Life-cycle state diagram, with the states each station passes through

```
          new StationThread(...)
                  │
                  ▼
               [NEW]   ── observed: "before start: state=NEW isAlive=false"
                  │ start()
                  ▼
            [RUNNABLE] ◄──────────────┐  (scanning the next script)
                  │ Thread.sleep(2..5)│ time elapses
                  ▼                   │
          [TIMED_WAITING] ────────────┘ ── observed: "20 ms after start: state=TIMED_WAITING isAlive=true"
                  │ run() returns after 25 scripts
                  ▼
           [TERMINATED]  ── observed: "after join: state=TERMINATED isAlive=false"

 (other states, not used by a station:  BLOCKED  – waiting for a monitor lock;
                                        WAITING  – wait()/join() without timeout,
                                                   which is the state of *main* while it joins)
```

### Why calling `run()` instead of `start()` does not create a thread

`run()` is an ordinary method: calling it executes the body **on the caller's thread**, synchronously. `start()` asks the JVM to create a new OS thread, which then calls `run()`. Evidence:

```
=== C. run() instead of start() ===
  task executing in thread: main            <- direct.run()
  after run(): Station-X state=NEW (never started)
  task executing in thread: Station-X       <- direct.start()
```

After `run()`, the thread object is still `NEW`. No parallelism happened, and the four "stations" would scan one after another.

### How `join()` ensures the final total is printed only after all stations finish

`t.join()` blocks the calling thread (`main`, in state WAITING) until `t` is TERMINATED. `main` joins all four stations before summing `scanned`, so the sum cannot be computed early.

`join()` also creates a **happens-before** edge: all writes made by the station (its `scanned` count) are guaranteed to be visible to `main` after `join()` returns. That is why the total is always exactly 100, even though `scanned` is not `volatile` in `StationThread`. Without `join()`, `main` could print a partial total, or even 0.

## Student B – Behavioural Experiments

### Ten runs – finish order (same random seeds every run, so the work per station is identical)

| Run | Finish order |
|---|---|
| 1 | S1, S2, S3, S4 |
| 2 | S2, S1, S4, S3 |
| 3 | S2, S1, S4, S3 |
| 4 | S2, S1, S3, S4 |
| 5 | S1, S2, S3, S4 |
| 6 | S1, S2, S4, S3 |
| 7 | S1, S2, S3, S4 |
| 8 | S1, S2, S4, S3 |
| 9 | S2, S1, S3, S4 |
| 10 | S1, S2, S4, S3 |

There are **6 different orders in 10 runs**. S1/S2 and S3/S4 swap places because their total sleep times are close, so tiny differences in wake-up latency decide who finishes first.

### Thread priorities

CPU-bound stations were used, because a sleeping thread does not compete for the CPU at all. S1 = `MAX_PRIORITY` (10), others = `MIN_PRIORITY` (1). 10 runs:

| Runs | Result |
|---|---|
| 3–9 | S1 finished first in 7 of 10 runs (runs 3–9) |
| 1, 2, 10 | S3, S2 and S2 finished first |

**Interpretation:** there is no reliable effect. S1 is also *started first*, which by itself explains most of its wins. With 4 threads on a 10-core machine every thread gets its own core, so there is nothing to arbitrate. On macOS, HotSpot also does not map Java priorities to OS priorities by default (`ThreadPriorityPolicy`). Priorities are a *hint* and must never be used for correctness.

### A daemon station

```
### java ScannerStations daemon
  main ends now; daemon has scanned 7 of 500, isAlive=true
  (JVM exited - no 'daemon station finished' line was printed)
```

The JVM exits as soon as the last **non-daemon** (user) thread ends. The daemon station was killed after 7 of 500 scripts. Its `finally` blocks and the "finished" message never ran, so **193 + 300 scripts were silently lost**. Daemon threads therefore suit only background helpers (e.g. a progress monitor), never real scanning work.

### Explanation using the scheduler

The JVM uses native OS threads, and macOS's scheduler decides which runnable thread runs on which core and for how long. After each `sleep()`, a station becomes runnable again only when the OS timer fires and the scheduler dispatches it. That delay varies by microseconds to milliseconds, depending on other processes, core power states and timer coalescing. Over 25 sleeps these small variations accumulate and reorder stations whose work is similar. Priorities are only hints to that scheduler.

## Joint conclusion – why output order differs between runs, and why correctness must not depend on it

The order depends on **when the OS scheduler runs each thread**. That timing is non-deterministic: it depends on other load, the number of cores, timer resolution, JIT compilation and garbage-collection pauses, none of which the program controls. The Java Memory Model also guarantees ordering only through synchronisation (`join`, locks, `volatile`). Without those, two threads' actions have **no defined order**.

A correct program must give the same *result* (the total of 100, an identical report) for **every** possible interleaving. It must coordinate with `join()`, synchronised collections (`Collections.synchronizedList` for `finishOrder`) or futures, never with assumptions such as "station 1 is faster" or "priority 10 runs first". Code that only works for the order seen in testing will fail on a different machine or under load.

## Alternative Concept and Complexity Analysis – `ExecutorService` with a fixed pool

```java
ExecutorService pool = Executors.newFixedThreadPool(p);
List<Future<Integer>> results = new ArrayList<>();
for (Batch b : batches) results.add(pool.submit(() -> scan(b)));   // Callable<Integer>
int total = 0;
for (Future<Integer> f : results) total += f.get();                // replaces join()
pool.shutdown();
```

**Measured:** n = 400 scripts, 5 ms each (`Thread.sleep` overshoots to about 6.2 ms on macOS, so all times are about 1.24× the ideal).

| Approach | Time (ms) | Ideal n/p × 5 ms |
|---|---|---|
| 4 manual Threads (static split) | 618 | 500 |
| fixed pool p = 1 | 2,475 | 2,000 |
| fixed pool p = 2 | 1,233 | 1,000 |
| fixed pool p = 4 | 616 | 500 |
| fixed pool p = 8 | 311 | 250 |
| fixed pool p = 16 | 158 | 125 |

Completion time follows **⌈n/p⌉ × t** almost exactly: doubling p halves the time. Here the work is waiting (I/O-like), so even p = 16 on 10 cores keeps scaling. For CPU-bound scanning, the curve would flatten at p ≈ the number of cores.

| | Manual `Thread` objects | Fixed thread pool |
|---|---|---|
| Expected completion time | ≈ n/p batches × t, with a *static* split: if one station gets slower scripts, the others sit idle | ≈ n/p × t with *dynamic* load balancing: idle workers take the next task from the queue |
| Thread creation | p threads created per run, plus one per new batch if reused naively | p threads created once and reused for any number of tasks |
| Memory | p × stack size; reserved stack is 2 MB per thread here (`ThreadStackSize = 2048` KB) – mostly virtual, committed on demand | the same p × 2 MB, plus an O(n) task queue (one small object per submitted task) |
| Result collection | shared fields + `join()` | `Future.get()` returns each result *and* rethrows the task's exception as `ExecutionException` |

Big-O: time is O(n/p · t) for both; space is O(p · stack + n) for the pool and O(p · stack) for manual threads. The pool wins on *engineering*: reuse, load balancing, exception propagation and a clean shutdown.
