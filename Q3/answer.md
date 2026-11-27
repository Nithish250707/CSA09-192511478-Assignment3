# Q3 – Resource Safety, Debugging and Robust Code (K4)

> Pair-work note: the roles are reversed in this question (A = debugging, B = design). I did both parts.

## Shared group work

`ReportGenerators.java` contains two versions:

* **`manual()`**: try–catch–finally with manual `close()`. Each store reader is closed at the end of its loop iteration, and the writer is closed in `finally`.
* **`withResources()`**: try-with-resources for the writer *and* for each store reader.

Input: `stores/` holds 5 valid store files; `stores_bad/` is the same set, except that `store3.csv` has the amount `TWENTY` on line 3.

**Both versions produce the same report** (real output):

```
manual ok=true, twr ok=true, reports identical: true
    store1.csv,502.00 ... store5.csv,2102.00
    TOTAL,6510.00
open fds before=5 after=5
```

## Student A – Debugging and Experimental Analysis

### Breakpoints and step-through: where the manual version leaks

A scripted `jdb` session (`debug.sh`, full transcript in `debug_session.txt`) set breakpoints on line 27 (`new FileReader(path)` – *open*) and line 35 (`in.close()` – *close*), plus a catch-point on `NumberFormatException`:

```
Breakpoint hit: ... ReportGenerators.manual(), line=27     path = "stores_bad/store1.csv"   <- open store1
Breakpoint hit: ... ReportGenerators.manual(), line=35     path = "stores_bad/store1.csv"   <- close store1  ✔
Breakpoint hit: ... ReportGenerators.manual(), line=27     path = "stores_bad/store3.csv"   <- open store3
Exception occurred: java.lang.NumberFormatException (to be caught at: ReportGenerators.manual(), line=43)
  [4] ReportGenerators.manual (ReportGenerators.java:33)
main[4] locals:  path = "stores_bad/store3.csv"  in = instance of java.io.BufferedReader(id=937)
                 line = "item2,TWENTY"  total = 310.5
```

For store3, the line-35 breakpoint is **never hit**. Execution jumps from line 33 (`Double.parseDouble`) straight to the catch block at line 43. The local variable `in` still refers to an **open** `BufferedReader`. When the method returns, that reference is lost, but the file descriptor stays open. This is the leak point.

### An input that makes the manual version fail part-way

`stores_bad/store3.csv`:

```
item,amount
item1,310.50
item2,TWENTY      <- NumberFormatException
item3,330.50
```

The manual version wrote a **partial report**, which explains the complaint that "the report is sometimes incomplete":

```
store1.csv,502.00
store2.csv,902.00          (store3–5 and TOTAL missing)
```

### Files left open in each version, for the same failing input

Open file descriptors were counted from `/dev/fd` before and after each version:

| Version | fds before | fds after | Leaked |
|---|---|---|---|
| manual close() | 5 | 6 | **1** (the store3 reader) |
| try-with-resources | 6 | 6 | **0** |

**Reproducing "Too many open files"** (a server re-running the report every time, under `ulimit -n 64`):

```
after run 10: open fds = 16
after run 20: open fds = 26
...
after run 50: open fds = 56
run 59 FAILED: java.io.FileNotFoundException: stores_bad/store1.csv (Too many open files)  (open fds = 64)
```

The leak is exactly **1 descriptor per failed run**. At run 59 the process hit the OS limit, and from then on even *valid* files (`store1.csv`) could not be opened. That is the production symptom. (The JDK's `Cleaner` would eventually close leaked streams after a garbage collection, but nothing guarantees when, so the program cannot rely on it.)

### Logging which file caused the failure

Both versions record the file being processed in `current` and log it through `java.util.logging`:

```
[LOG SEVERE] manual: failed while processing stores_bad/store3.csv: java.lang.NumberFormatException: For input string: "TWENTY"
[LOG SEVERE] try-with-resources: failed while processing stores_bad/store3.csv: java.lang.NumberFormatException: For input string: "TWENTY"
```

## Student B – Design and Correctness

### Every resource and where it must be closed

| Resource | Opened at | Must be closed |
|---|---|---|
| `FileWriter` → `BufferedWriter out` (report file) | start of run | after the last line is written, **on every path**. Closing also *flushes* the buffer; without it the report can be empty |
| `FileReader` → `BufferedReader in` (one per store, f in total) | each loop iteration | at the end of **that iteration**, on every path, before the next store is opened |
| (underlying OS file descriptor of each) | with the `FileReader`/`FileWriter` | closed by closing the outermost wrapper (`BufferedReader.close()` closes the `FileReader`) |

### Why the manual version can leak when an exception occurs

`in.close()` is an ordinary statement placed *after* the loop that can throw. When `parseDouble` throws, control leaves the `try` block immediately: statements after the throw point, including `close()`, are skipped. The `finally` only closes `out`, because `in` is a local variable *inside* the loop and is out of scope there. Fixing this by hand needs a nested `try { … } finally { if (in != null) in.close(); }` around every reader, which is verbose, and a `close()` that throws can still *hide* the original exception.

### How try-with-resources guarantees closing, and the order

```java
try (BufferedWriter out = ...) {
    for (...) {
        try (BufferedReader in = ...) { ... }   // in.close() runs here on success OR exception
    }
}                                               // out.close() runs here on success OR exception
```

* The compiler generates the equivalent of a `finally` that calls `close()` on every resource whose initialiser **completed**, whether the block ends normally, by `return`, or by an exception.
* Resources are closed in **reverse order of declaration** (LIFO), so a resource is never closed while a resource declared later, which may depend on it, is still open.
* Real output with two resources:

```
open storeReader
open reportWriter
body throws
close reportWriter      <- last opened, first closed
close storeReader
```

### Suppressed exceptions – what and when

A suppressed exception occurs when the **body throws** and then **`close()` also throws**. Java keeps the *body's* exception as the primary one, since it is the real cause, and attaches the close failures to it with `addSuppressed()`, so no information is lost. Real output:

```
caught primary: java.io.IOException: read failed in body
suppressed   : java.lang.IllegalStateException: close failed: reportWriter
suppressed   : java.lang.IllegalStateException: close failed: storeReader
```

In the manual version, an exception thrown from `close()` inside `finally` would **replace** the body's exception, and the original cause would disappear.

## Joint engineering decision – when try-with-resources is not enough

try-with-resources guarantees that *resources are released*. It does not make the *report* correct or the program robust:

1. **Partial output.** If store3 fails half-way, the writer is closed cleanly, but the report still contains only store1 and store2, which looks like a valid but smaller report. Fix: write to `report.tmp` and atomically rename it (`Files.move(..., ATOMIC_MOVE)`) only after success, or write an explicit "INCOMPLETE" marker.
2. **One bad line aborts everything.** Robustness needs a *policy*: skip and log bad lines (or a bad store), continue with the others, and report failures in a summary (see Q7).
3. **Resources not opened in the `try` header.** A stream returned by a factory and stored in a field, or work handed to another thread, is not covered.
4. **Non-`AutoCloseable` resources and external state.** Locks, temp files to delete, database transactions to roll back, or a half-written network upload all need explicit `finally`, rollback or cleanup logic.
5. **The OS limit itself.** Opening thousands of store files *at the same time* (e.g. in parallel threads) can still exceed `ulimit` even if each is closed. This needs bounded concurrency.
6. **`close()` failures** (disk full during the final flush) are only *recorded* as suppressed exceptions. The code must still check and react to them.

## Alternative Concept and Complexity Analysis

The three closing strategies are: (a) try-with-resources (implemented), (b) explicit `close()` calls in a `finally` block, and (c) no closing at all.

```java
// (b) explicit close in finally - correct if done per reader:
for (String path : stores) {
    BufferedReader in = null;
    try {
        in = new BufferedReader(new FileReader(path));
        ...
    } finally {
        if (in != null) try { in.close(); } catch (IOException e) { log(e); }   // can hide body exception
    }
}

// (c) no closing at all:
for (String path : stores) { BufferedReader in = new BufferedReader(new FileReader(path)); ... }   // never closed
```

For a report reading f store files with L lines in total:

| Version | Processing time | Max file handles open at the moment of a failure | Handles still open *after* a failed run |
|---|---|---|---|
| try-with-resources | O(L) | **2** (1 reader + the writer) | 0 |
| explicit close in `finally` (per reader) | O(L) | 2 | 0 – but more code, and a `close()` exception can mask the original |
| my original manual version (close only on success) | O(L) | 2 | **1 per failed run**, so the process fails after about `ulimit − baseline` failed runs (measured: run 59 at `ulimit -n 64`) |
| no closing at all | O(L) | up to **f + 1** (every reader opened so far + the writer) | f + 1 per run (until a GC `Cleaner` happens to run), so the OS limit is reached after about `ulimit / (f + 1)` runs |

* **Time:** every version reads each line once, so O(L). Closing adds O(1) per file, which is negligible.
* **Space:** the real difference is in **file handles**. Correct closing keeps the count bounded by O(1), independent of f and of the number of runs. Without closing it grows as O(f) per run and O(f × runs) over the life of the server.
