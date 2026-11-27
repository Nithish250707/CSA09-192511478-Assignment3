# Q1 – Handling Failure in a Bank ATM Simulation (K4)

> **Pair-work note.** No partner was assigned in the brief I received, so I (Nithish G, 192511478) carried out **both** the Student A and Student B responsibilities in this repository. The assignment also requires both students to execute every question in their own repository. Each section is labelled.

## Shared group work

### Every point at which the withdrawal can fail, classified

| # | Failure point | Exception / error | Kind |
|---|---|---|---|
| 1 | Card-reader file missing | `FileNotFoundException` | **checked** (`IOException`) |
| 2 | Card-reader read error (device fault) | `IOException` | **checked** |
| 3 | Card holds garbage (`ABC-1001`) | `NumberFormatException` from `Long.parseLong` | unchecked |
| 4 | User types letters (`abc`) | `InputMismatchException` from `Scanner.nextInt` | unchecked |
| 5 | User enters nothing (input stream ends) | `NoSuchElementException` | unchecked |
| 6 | Negative / zero amount, or not a multiple of 100 | `IllegalArgumentException` (thrown by `withdraw`) | unchecked |
| 7 | Amount > balance | `InsufficientFundsException` (user-defined) | **checked** – the caller must handle it and can recover |
| 8 | Account not in the bank's map | `NullPointerException` (unboxing `null`) | unchecked – a **bug**, deliberately left uncaught to show a stack trace |
| 9 | JVM out of memory / stack overflow | `OutOfMemoryError`, `StackOverflowError` | **Error** – not caught; the program cannot recover |

The implementation (`AtmWithdrawal.java`) uses `try`, six `catch` clauses including the multi-catch `InputMismatchException | NumberFormatException`, and `finally`. Tests T1–T9 were run with valid and invalid inputs.

## Student A – Exception Design

### Relevant part of the Throwable hierarchy

```
java.lang.Object
 └── Throwable
      ├── Error  (unchecked, not caught)
      │    └── VirtualMachineError → OutOfMemoryError, StackOverflowError
      └── Exception
           ├── IOException  (checked)
           │    └── FileNotFoundException  (checked)
           ├── InsufficientFundsException  (checked, user-defined)
           └── RuntimeException  (unchecked)
                ├── IllegalArgumentException
                │    └── NumberFormatException
                ├── NoSuchElementException
                │    └── InputMismatchException
                └── NullPointerException
```

### Failure point → exception → kind → where it is caught

| Failure point | Exception type | Checked? | Where caught |
|---|---|---|---|
| Card file missing | `FileNotFoundException` | checked | `main`, 1st catch |
| Card read failure | `IOException` | checked | `main`, 2nd catch (`readCard` declares `throws IOException`) |
| Corrupt card / letters typed | `NumberFormatException` / `InputMismatchException` | unchecked | `main`, 3rd catch (multi-catch) |
| No input | `NoSuchElementException` | unchecked | `main`, 4th catch |
| Negative or non-multiple-of-100 amount | `IllegalArgumentException` | unchecked | `main`, 5th catch |
| Amount > balance | `InsufficientFundsException` | checked | `main`, 6th catch (`withdraw` declares `throws`) |
| Unknown account | `NullPointerException` | unchecked | **not caught** – terminates `main` after `finally` |
| JVM failure | `Error` | unchecked | not caught |

### Justifying the order of the catch blocks

The rule is **subclass before superclass**, because the first matching `catch` wins.

* `FileNotFoundException` must come before `IOException` (its superclass), otherwise the "missing card" message could never be shown.
* The multi-catch `InputMismatchException | NumberFormatException` must come before `NoSuchElementException` and `IllegalArgumentException`, because `InputMismatchException extends NoSuchElementException` and `NumberFormatException extends IllegalArgumentException`. In the wrong order, letters typed would be reported as "no amount entered" and a corrupt card as "invalid amount".
* The two members of the multi-catch must not be related to each other (Java forbids `A | B` when B is a subclass of A).
* Unrelated types (`InsufficientFundsException`) can go anywhere.

### What the finally block guarantees

`finally` runs on **every** path out of the `try`: normal completion (T1), every caught exception (T2–T8) and even the **uncaught** `NullPointerException` (T9: `[finally] Card ejected, session closed` is printed *before* the stack trace). In this program it guarantees:

* the card is ejected (the user's card is never kept);
* the `Scanner` on `System.in` is closed;
* the session-end log line is written.

It would *not* run only if the JVM itself dies (`System.exit()` inside `try`, power loss, or the process being killed).

## Student B – Testing and Tracing

### Test inputs, the catch block reached, and whether finally ran (real output, `output.txt`)

| Test | Card file | Typed | Path intended | Catch block executed | finally? | Result |
|---|---|---|---|---|---|---|
| T1 | 1001 | 2000 | normal | none | ✔ | Dispensed Rs 2000, balance 3000 |
| T2 | 1001 | `abc` | letters | `InputMismatchException \| NumberFormatException` | ✔ | "Not a number: InputMismatchException" |
| T3 | 1001 | `-500` | negative | `IllegalArgumentException` | ✔ | "amount must be positive: -500.0" |
| T4 | 1002 (Rs 300) | 1000 | over balance | `InsufficientFundsException` | ✔ | "requested Rs 1000.0 but balance is Rs 300.0" |
| T5 | (missing) | 500 | missing file | `FileNotFoundException` | ✔ | "Card reader file missing" |
| T6 | `ABC-1001` | 500 | corrupt card | multi-catch (as `NumberFormatException`) | ✔ | `For input string: "ABC-1001"` |
| T7 | 1001 | *(nothing)* | no input | `NoSuchElementException` | ✔ | "No amount entered" |
| T8 | 1001 | 250 | not multiple of 100 | `IllegalArgumentException` | ✔ | "multiples of Rs 100 only" |
| T9 | 9999 (unknown) | 500 | unknown account | **none – uncaught** | ✔ (before the trace) | stack trace, exit code 1 |

### Stack trace of one uncaught exception (T9), explained line by line

```
Exception in thread "main" java.lang.NullPointerException: Cannot invoke "java.lang.Double.doubleValue()" because "bal" is null
	at AtmWithdrawal.withdraw(AtmWithdrawal.java:35)
	at AtmWithdrawal.main(AtmWithdrawal.java:49)
```

* **Line 1:** the thread that died (`main`), the exception class, and the *helpful NPE message* (JDK 14+). Unboxing `Double bal` called `doubleValue()` on `null`, because account 9999 is not in the map.
* **Line 2:** the top frame, where the exception was *created*: method `withdraw`, source line 35 (`double balance = bal;`).
* **Line 3:** the caller, `main` at line 49 (`double left = withdraw(account, amount);`). No catch in `main` matches an NPE, so it propagated out of `main` and the default handler printed this trace. Frames are listed innermost first, so read the trace from the top to find the cause and from the bottom to see how the program got there.

The fix is to check `if (bal == null) throw new AccountNotFoundException(...)`. The NPE is shown on purpose: it is a programming bug, and catching it would only hide the bug.

### Broader catch placed before a narrower one

`negative/WrongOrder.java` puts `NoSuchElementException` before `InputMismatchException`, and `negative/CatchAllFirst.java` puts `Exception` before `NumberFormatException`. Real compiler output:

```
negative/WrongOrder.java:13: error: exception InputMismatchException has already been caught
negative/CatchAllFirst.java:8: error: exception NumberFormatException has already been caught
```

Java turns the mistake into a **compile-time error** (an unreachable catch clause), so the wrong order can never ship.

## Joint design question – one `catch (Exception e)` for everything?

**When it is acceptable:**

1. At the **outermost boundary** of a program or a thread (`main`, a request handler, the ATM's top-level session loop), *after* all specific handlers, as a last-resort safety net that logs the full stack trace, shows "Service temporarily unavailable" and keeps the machine running for the next customer.
2. When every failure really has the **same recovery** (e.g. a retry loop around one remote call).

**What is lost by catching only `Exception`:**

* **Specific recovery:** a missing card ("insert card again") is a different action from insufficient funds ("show balance, ask for a smaller amount"), which is different from letters typed ("re-enter the number").
* **Accurate messages:** the customer sees one generic message.
* **Compiler help:** checked exceptions no longer force each failure to be thought about separately.
* **Bug visibility:** the T9 `NullPointerException` (a programming bug) would be swallowed as if it were a user error, and the defect would never be fixed.
* It also catches `InterruptedException` and other exceptions the code did not mean to handle.

`Error`s are not caught by `catch (Exception)` at all; that is correct, as they cannot be recovered from.

## Alternative Concept and Complexity Analysis – no exceptions, error codes

```java
static int withdraw(Scanner in) {
    if (!in.hasNextInt()) { if (in.hasNext()) in.next(); return ERR_NOT_NUMBER; }   // -1
    int amount = in.nextInt();
    if (amount <= 0) return ERR_NEGATIVE;                                           // -2
    if (amount > balance) return ERR_FUNDS;                                         // -3
    balance -= amount;
    return OK;
}
// caller:
int code = withdraw(in);
if (code == ERR_NOT_NUMBER) ... else if (code == ERR_NEGATIVE) ... else if ...
```

Real output: `'500' -> 0, 'abc' -> -1, '-200' -> -2, '999999' -> -3`.

**Readability and number of checks:**

* With error codes, **every** caller on every level must test the return value: `readCard`, `withdraw` and the session loop each need their own `if (code < 0)` chain.
* For f failure kinds and d call levels, that is O(f × d) checks scattered through the normal logic. A forgotten check silently continues with a wrong value.
* `-1` can also collide with a legitimate return (a balance can't be −1, but a "remaining amount" might be 0).
* Exceptions keep the "happy path" linear, and they propagate automatically to the one place that can handle them.
* `hasNextInt()` does make the pre-check cheaper and cleaner for *expected* user typos, so the best design uses both: pre-check predictable input errors, and use exceptions for exceptional conditions (missing card, I/O failure, insufficient funds).

**Measured cost** (`AtmErrorCodes`, 1,000,000 failures, after warm-up):

| Mechanism | Time per failure | Space per failure |
|---|---|---|
| Return an error code | 0.0–2.4 ns | none (an `int` in a register) |
| `throw` + `catch` of a normal `Exception` | **≈ 191 ns** | 1 exception object + the captured stack trace: O(d) for call depth d (`fillInStackTrace` walks the stack; `StackTraceElement` objects are created only if `getStackTrace()` is called) |
| `throw` + `catch` without a stack trace (`super(msg, null, false, false)`) | ≈ 3.5 ns | 1 small object, O(1) |

So **Big-O time per failure:** error code O(1); exception O(d), dominated by capturing the stack trace, not by the `throw` itself. In absolute terms, 191 ns per failed withdrawal is nothing next to a human typing a PIN. Exceptions should simply not be used for control flow in a tight loop of millions of iterations.
