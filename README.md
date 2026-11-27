# CSA09 – Assignment 3: Exception Handling and Multithreading (CO3)

| | |
|---|---|
| **Name** | Nithish G |
| **Register Number** | 192511478 |
| **Programme** | B.E. Computer Science and Engineering, SIMATS Engineering, Chennai |
| **Course** | Programming in Java – CSA0921 (printed as CSA09 in the assignment) |
| **Assignment** | 3 – CO3: exceptions, try-with-resources, debugging, threads, synchronization, deadlock, executors, concurrent collections |
| **Mode** | Pair work (see the contribution record below) |
| **Java version** | `java version "21.0.12.1" 2026-08-18 LTS` (Java HotSpot 64-Bit Server VM, build 21.0.12.1+1-LTS-4) |
| **Report** | [REPORT.md](REPORT.md) – all written answers in question order |

## Repository layout

Each `Qn/` folder has:

* the Java source, plus any input files (`Q1/tests/`, `Q3/stores*/`, `Q7/sample/`);
* `output.txt` – the real console output, including test tables, stack traces and logs;
* `answer.md` – the analysis, labelled Student A, Student B or Joint;
* `run.sh` – the exact commands used.

`Q3/debug_session.txt` is a real `jdb` breakpoint session, produced by `Q3/debug.sh`. `Q7/data/` (10,000 generated files) is created at run time and is not committed.

## Compile and run

Run from the repository root; only a JDK is needed. `bash run_all.sh` runs everything. Q6 takes about 3 minutes, because of the timed deadlock, `notify` and `tryLock` trials.

| Q | Compile | Run |
|---|---|---|
| Q1 | `cd Q1 && javac -g -d out AtmWithdrawal.java AtmErrorCodes.java` | `printf '2000\n' \| java -cp out AtmWithdrawal tests/card_1001.txt` (see `run.sh` for all 9 tests) · `java -cp out AtmErrorCodes` |
| Q2 | `cd Q2 && javac -d out/complete complete/StorageDemo.java && javac -g -d out/portal portal/*.java` | `java -cp out/complete StorageDemo` · `java -cp out/portal PortalDemo` |
| Q3 | `cd Q3 && javac -g -d out ReportGenerators.java ReportDemo.java DebugManual.java` | `(ulimit -n 64; java -cp out ReportDemo 200)` · `bash debug.sh` (jdb session) |
| Q4 | `cd Q4 && javac -d out ScannerStations.java` | `java -cp out ScannerStations` · `java -cp out ScannerStations daemon` |
| Q5 | `cd Q5 && javac -d out/unsync unsync/SeatBooking.java && javac -d out/sync SeatBooking.java && javac -d out RaceStudy.java` | `java -cp out/unsync SeatBooking` (×10) · `java -cp out/sync SeatBooking` (×10) · `java -cp out RaceStudy` |
| Q6 | `cd Q6 && javac -d out/w TokenCounter.java && javac -d out/i ifvariant/TokenCounter.java && javac -d out WalletTransfers.java ProducerConsumerStudy.java` | `java -cp out/w TokenCounter` · `java -cp out/i TokenCounter` · `java -cp out WalletTransfers` · `java -cp out ProducerConsumerStudy` |
| Q7 | `cd Q7 && javac -d out ResultProcessor.java Q7Main.java` | `java -Xss512k -cp out Q7Main` |

## Result of each question

| Q | Result |
|---|---|
| Q1 – ATM | 9 failure points classified (checked, unchecked, Error). 9 tests hit every catch block, and `finally` ran in all of them, including the uncaught NPE (stack trace explained). Wrong catch order is a compile error (real `javac` output). An exception costs ≈ 191 ns (≈ 3.5 ns without a stack trace) vs ≈ 0–2 ns for an error code. |
| Q2 – Exam portal | Checked `ExamException` hierarchy across controller, service and storage layers. **Completed `StorageDemo` matches the expected output**; without the cause, the program crashes with an NPE. Full chained stack trace vs a cause-less rethrow shown. Stack-trace capture cost grows with depth (231 ns → 954 ns). |
| Q3 – Sales report | The manual version leaks 1 file per failure (jdb session shows `in.close()` never reached), and under `ulimit -n 64` it failed at run 59 with **"Too many open files"**. try-with-resources leaks 0, closes in reverse order, and records suppressed exceptions. |
| Q4 – Scanner stations | Thread vs Runnable, states NEW → TIMED_WAITING → TERMINATED observed, `run()` executes on `main`. 6 distinct finish orders in 10 runs. Priorities had no reliable effect. A daemon station was killed after 7/500 scripts. Pool time ≈ n/p × t (2,475 → 158 ms for p = 1 → 16). |
| Q5 – Seat booking | The given `main` showed 10 in all 10 runs, even unsynchronized (explained). Designed tests reproduce double booking reliably (900 in 10 runs) and without delay (4,607). Synchronized method 22.7 ms vs block 3.3 ms vs CAS 3.2 ms for 10k bookings, all correct. |
| Q6 – Deadlock / producer–consumer | Naive transfers deadlocked in 5/5 trials (detected by ThreadMXBean); lock ordering 5/5 OK, money conserved. **Completed `TokenCounter` = 210 (expected)**; `if` instead of `while` gave a wrong sum (135) in 4/30 runs; `notify()` hung in 30/30 trials, `notifyAll()` in 0/30. Counter stayed within 0..5 over 120,000 operations. |
| Q7 – Result processor | ExecutorService + Callable + Future + ConcurrentHashMap. Hand-checked sample matches (5 ok, 4 corrupt, 1 missing, average GPA 7.48). 10,000 files with 226 corrupt + 96 missing: counts exact for every pool size, ≈ 0.27 s. I/O-bound, so threads do not help; with CPU-heavy work it scales 1,787 → 330 ms (p = 1 → 8). |

## Individual Contribution Record

The assignment brief did not name a partner, so I carried out **both** roles in this repository. The assignment also requires each student to execute every question in their own repository. The answers keep the Student A / Student B labels so that each part can be checked separately.

| Question | Student A – Primary responsibility | Student B – Primary responsibility | Joint |
|---|---|---|---|
| Q1 | Throwable hierarchy, failure table, catch order, `finally` guarantee – *Nithish G* | 9 test inputs, catch/finally table, stack trace, wrong-order demo – *Nithish G* | single `catch (Exception)` question |
| Q2 | Class diagram, checked/unchecked reasons, propagation path – *Nithish G* | Scenarios, `getCause()` and chained trace, lost-cause demo, test table – *Nithish G* | why wrap `IOException` |
| Q3 | jdb debugging, failing input, open-file counts, logging – *Nithish G* | Resources, leak explanation, try-with-resources order, suppressed exceptions – *Nithish G* | when try-with-resources is not enough |
| Q4 | Thread vs Runnable, state diagram, `run()` vs `start()`, `join()` – *Nithish G* | 10 runs, priorities, daemon, scheduler explanation – *Nithish G* | why order varies |
| Q5 | Reliable race test, counts, interleaving, critical section – *Nithish G* | 10k-booking timings, method vs block, static sync, intrinsic locks – *Nithish G* | 20-screen strategy |
| Q6 | wait-in-loop, notify vs notifyAll, size trace, sleep vs wait – *Nithish G* | deadlock pair, four conditions, lock ordering, why it works – *Nithish G* | common ground of both |
| Q7 | Stage 2 design (executor, collections, exceptions, diagram, pseudocode) – *Nithish G* | Stage 3 implementation, fault injection, pool timings, hand check – *Nithish G* | Stages 1 and 4 |

## Assumptions and notes

* **Pseudocode** uses the same `ALGORITHM / BEGIN … END` numbered-step format as in Assignment 1. The SIMATS standard document was not included with the PDF.
* **Screenshots:** the programs are console programs, so `output.txt` (and `Q3/debug_session.txt` for the debugger) is the evidence.
* **Q3 breakpoints:** stepping was done with the JDK's `jdb` debugger in a scripted session (`debug.sh`), so the transcript is reproducible. An IDE was not used.
* **Q5 first run:** the given `main()` did not show a double booking in 10 runs. This is reported as observed, explained, and reproduced with the designed tests.
* Timings come from one 10-core Apple Silicon Mac running JDK 21. They are means after warm-up unless a table says otherwise.
