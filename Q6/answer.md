# Q6 – Deadlock and Inter-Thread Communication (K4)

> Pair-work note: Student A and Student B parts were both done by me (Nithish G).

## Shared group work

* **Part A (`WalletTransfers.java`):** `transferNaive` locks `from` then `to`, so two opposite transfers deadlock. `transferOrdered` always locks the lower wallet id first, so it cannot deadlock. Tested with 2 threads (5 trials) and 8 threads × 25,000 random transfers.
* **Part B (`TokenCounter.java`):** the producer–consumer counter of capacity 5, built with `wait()` and `notifyAll()`. `ProducerConsumerStudy.java` instruments it and tests `notify()` against `notifyAll()`.

### Complete the Code – `TokenCounter.java`

```java
// TODO 1
while (count == buffer.length) wait();
buffer[in] = token; in = (in + 1) % buffer.length; count++;
notifyAll();
// TODO 2
while (count == 0) wait();
int token = buffer[out]; out = (out + 1) % buffer.length; count--;
notifyAll();
return token;
```

* **Expected:** `Sum of tokens collected: 210 (expected 210)`
* **Actual:** 210 in **5/5** runs ✔ (1 + 2 + … + 20 = 210)

**`while` changed to `if`** (`ifvariant/`, 30 runs):

| Outcome | Runs |
|---|---|
| `Sum of tokens collected: 210` | 26 |
| `Sum of tokens collected: **135**` | **4** |

With `if`, a thread that wakes up does **not re-check** the condition.

* Scenario: the counter holds 1 token, and both students are waiting. The cook's `notifyAll()` wakes *both* students. Student 1 takes the token. Student 2 resumes after its `if`, assumes a token is there, and "takes" from an **empty** counter.
* That is why `count` goes to −1 and `out` moves past `in`. The student then reads a stale slot (a token already collected, or 0), and later real tokens are overwritten or never read.
* The sum comes out wrong (135 = 75 lost) even though the program "finished normally". The failure is intermittent (4/30), which makes it dangerous.

## Student A – Producer–Consumer Design

### Why `wait()` must be called inside a loop and inside a synchronized block

* **Inside `synchronized`:**
  * `wait()` releases the object's monitor and must own it. Otherwise it throws `IllegalMonitorStateException`.
  * More fundamentally, the *check* (`count == 0`) and the *wait* must be atomic with respect to the producer's `count++` + `notify`. Without the lock, the producer could add a token and notify between the consumer's check and its `wait()`. That notification would be lost and the consumer would sleep forever (the *lost wake-up* problem).
* **Inside a loop:** when `wait()` returns, the condition may *no longer hold*:
  1. `notifyAll()` woke several threads, and another one consumed the token first (the `if` failure above).
  2. Spurious wake-ups are allowed by the JVM specification.
  3. The notification was meant for a different kind of waiter (a producer woke a producer).

  `while` re-tests after every wake-up, so the thread only proceeds when the condition is true *and it holds the lock*.

### `notify()` instead of `notifyAll()` with several consumers

Experiment: 2 cooks + 2 students on **one** monitor, capacity 1, 2,000 tokens per cook, 30 trials, 2 s timeout each.

| Wake-up call | Trials where every thread ended up waiting forever |
|---|---|
| `notify()` | **30 / 30** |
| `notifyAll()` | 0 / 30 |

Why: producers and consumers wait in the **same** wait set. `notify()` wakes *one arbitrary* thread.

* Example: the counter becomes empty and both cooks are waiting for space while both students are waiting for tokens. A student's `notify()` after a take can wake **the other student** instead of a cook.
* That student re-checks, finds the counter empty, and waits again. Nobody wakes the cooks, so all four threads wait forever: a hang, though not a lock-order deadlock.

`notifyAll()` wakes everyone, so the right kind of thread always gets a chance. In the 1-cook/2-student program from the PDF, `notify()` *could* work only by luck. My design uses `notifyAll()` deliberately.

### Counter size over time (never above 5, never below 0)

Instrumented run (1 cook × 20 tokens, 2 students). Size after each of the 40 operations:

```
[1, 2, 3, 4, 5, 4, 3, 2, 1, 2, 3, 4, 5, 4, 3, 2, 1, 0, 1, 2, 3, 4, 5, 4, 5, 4, 3, 2, 1, 0, 1, 0, 1, 0, 1, 2, 3, 2, 1, 0]
min = 0, max = 5, operations = 40, sum = 210
```

Stress run (3 cooks × 20,000 tokens, 4 students): **min = 0, max = 5 over 120,000 operations**, and the sum is correct. The bounds hold because every change to `count` happens under the lock *after* a `while` check.

### `sleep()` vs `wait()`

| | `Thread.sleep(ms)` | `obj.wait()` |
|---|---|---|
| Belongs to | `Thread` (static) | `Object` (any monitor) |
| Lock | **keeps** every lock it holds | **releases** the monitor of `obj` while waiting, and re-acquires it before returning |
| Woken by | time elapsing (or `interrupt()`) | `notify()`/`notifyAll()` on `obj`, a timeout, `interrupt()`, or spuriously |
| Must hold the monitor? | no | yes, otherwise `IllegalMonitorStateException` |
| Use | pacing or simulating time | waiting for a *condition* that another thread will make true |

Using `sleep()` inside `put()` while the counter is full would keep the lock, so no student could take a token. The counter would never empty, and the program would be stuck.

## Student B – Deadlock Analysis and Prevention

### A pair of transfers that reliably deadlocks

T1 transfers A→B (Rs 100) and T2 transfers B→A (Rs 50) at the same time. Each thread locks its *from* wallet, sleeps 50 ms (to make the overlap certain), then asks for its *to* wallet.

```
T1(A->B) is BLOCKED waiting for WalletTransfers$Wallet@41629346 held by T2(B->A)
T2(B->A) is BLOCKED waiting for WalletTransfers$Wallet@15aeb7ab held by T1(A->B)
deadlocked in 5/5 trials
```

`ThreadMXBean.findMonitorDeadlockedThreads()` found the cycle. The count of deadlocked threads grew by 2 each trial (2, 4, 6, 8, 10): **a deadlock never recovers**, and the stuck threads stay stuck until the JVM exits. The balances were never updated.

### The four Coffman conditions, as they appear in the program

| Condition | Where it is in `transferNaive` |
|---|---|
| 1. Mutual exclusion | `synchronized (wallet)`: only one thread can hold a wallet's monitor |
| 2. Hold and wait | T1 *holds* A's monitor while *waiting* for B's |
| 3. No pre-emption | Java cannot take a monitor away from a thread; it is released only when the block exits |
| 4. Circular wait | T1 waits for B (held by T2), and T2 waits for A (held by T1) – a cycle |

### Prevention with a consistent lock-ordering rule

```java
Wallet first  = from.id < to.id ? from : to;
Wallet second = from.id < to.id ? to   : from;
synchronized (first) { synchronized (second) { from.balance -= amt; to.balance += amt; } }
```

Results:

* The same opposite pair completed in **5/5 trials** (A = 950, B = 1050, as expected).
* Stress with 8 threads, 10 wallets and 200,000 random transfers finished in **44 ms**, and total money stayed exactly Rs 100,000.

### Why the rule works

It breaks condition 4, **circular wait**. Every thread acquires locks in increasing wallet id. A thread holding wallet i only ever waits for a wallet j > i. A cycle T1 → T2 → … → T1 would need ids i₁ < i₂ < … < i₁, which is impossible. So some thread in any chain holds the highest-numbered lock it needs, never waits, finishes, and releases its locks: progress is guaranteed. The other three conditions still hold, but all four are needed for a deadlock.

## Joint conclusion – what deadlock and producer–consumer have in common

Both are problems of **threads waiting for each other on shared resources**. Without a correct protocol, each thread waits for something that only another *waiting* thread can provide, so nobody progresses. A deadlock is a cycle of lock waits; a hung producer–consumer is a set of condition waits whose wake-ups were lost or sent to the wrong thread (the `notify()` hang above).

How each design guarantees progress:

* **Transfers:** a global lock order makes a waits-for cycle impossible, so at least one thread can always complete and release its locks.
* **Counter:**
  * `while (condition) wait()` under the lock means a thread proceeds only when it actually can;
  * `notifyAll()` after every state change means a thread that *can* now proceed is always woken;
  * the bounded buffer (0 ≤ count ≤ 5) means that whenever cooks are blocked, students can take, and vice versa.

## Alternative Concept and Complexity Analysis

```java
// Part B with a library queue
BlockingQueue<Integer> counter = new ArrayBlockingQueue<>(5);
counter.put(token);       // blocks while full
int t = counter.take();   // blocks while empty

// Part A with tryLock + timeout instead of ordering
if (from.lock.tryLock(1, MILLISECONDS)) try {
    if (to.lock.tryLock(1, MILLISECONDS)) try { move money; return; } finally { to.lock.unlock(); }
} finally { from.lock.unlock(); }
back off briefly and retry
```

**Measured:**

| Design | Time | Notes |
|---|---|---|
| Hand-written wait/notifyAll counter | 758–1,358 ns per token | 2 producers × 500,000, 2 consumers, capacity 5 |
| `ArrayBlockingQueue(5)` | 1,331–1,524 ns per token | same workload; the cost is dominated by threads blocking and waking on a 5-slot buffer, not by the queue |
| Lock-ordered transfers | **44 ms** for 200,000 transfers (0.2 µs each) | 1.000 lock attempts per transfer |
| `tryLock(1 ms)` + retry | **105,536 ms** for 200,000 transfers (0.53 ms each) | 4.211 attempts per transfer; no deadlock, but heavy contention makes threads give up and retry repeatedly (near-livelock), and each failed attempt can wait up to 1 ms |

| | Time per operation | Space | Error-proneness |
|---|---|---|---|
| wait/notifyAll counter | O(1) per put/take + wake-up cost | `int[5]` + 3 ints | **High**: needs `while`, `notifyAll`, the right lock; my experiments broke it two ways (`if` in 4/30 runs, `notify` in 30/30) |
| `ArrayBlockingQueue` | O(1) (`ReentrantLock` + two `Condition`s, `notFull`/`notEmpty`, so producers and consumers wait in *separate* queues and `signal()` is safe) | `Object[5]` + lock + 2 conditions | **Low**: the library is tested; no `wait` loops in user code |
| Lock ordering | O(1) per transfer: exactly 2 lock acquisitions | none extra (needs a unique, comparable id) | low, *if* every code path follows the order; one forgotten path reintroduces deadlock |
| `tryLock` + timeout | O(k) attempts under contention (measured 4.2); worst case unbounded retries (livelock) | a `ReentrantLock` per wallet | medium: no deadlock, but needs back-off tuning, and throughput collapsed here (2,400× slower) |

**Less error-prone choices:** `ArrayBlockingQueue` for Part B, and lock ordering for Part A. `tryLock` is useful as a *fallback* when a global order is impossible (locks acquired in an unknown sequence), not as the default.
