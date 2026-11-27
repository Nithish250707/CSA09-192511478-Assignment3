import java.util.Scanner;

/** Alternative: no exceptions - pre-check with hasNextInt() and return error codes. Also times both styles. */
public class AtmErrorCodes {
    static final int OK = 0, ERR_NOT_NUMBER = -1, ERR_NEGATIVE = -2, ERR_FUNDS = -3;
    static double balance = 5000;

    static int withdraw(Scanner in) {
        if (!in.hasNextInt()) { if (in.hasNext()) in.next(); return ERR_NOT_NUMBER; }
        int amount = in.nextInt();
        if (amount <= 0) return ERR_NEGATIVE;
        if (amount > balance) return ERR_FUNDS;
        balance -= amount;
        return OK;
    }

    static int codeStyle(int amount) { if (amount > balance) return ERR_FUNDS; return OK; }
    static void exceptionStyle(int amount) throws Exception { if (amount > balance) throw new Exception("insufficient"); }
    static final class LightException extends Exception {                       // no stack trace captured
        LightException() { super("insufficient", null, false, false); }
    }
    static void lightExceptionStyle(int amount) throws LightException { if (amount > balance) throw new LightException(); }

    public static void main(String[] args) {
        for (String input : new String[]{"500", "abc", "-200", "999999"}) {
            int code = withdraw(new Scanner(input));
            System.out.println("input '" + input + "' -> code " + code);
        }
        int n = 1_000_000; long sink = 0;
        for (int rep = 0; rep < 3; rep++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) sink += codeStyle(10_000);
            long t1 = System.nanoTime();
            for (int i = 0; i < n; i++) { try { exceptionStyle(10_000); } catch (Exception e) { sink++; } }
            long t2 = System.nanoTime();
            for (int i = 0; i < n; i++) { try { lightExceptionStyle(10_000); } catch (LightException e) { sink++; } }
            long t3 = System.nanoTime();
            if (rep > 0) System.out.printf("round %d: error code %.1f ns | throw+catch Exception %.1f ns | throw+catch without stack trace %.1f ns%n",
                    rep, (t1 - t0) / (double) n, (t2 - t1) / (double) n, (t3 - t2) / (double) n);
        }
        StackTraceElement[] st = new Exception().getStackTrace();
        System.out.println("Stack depth captured here: " + st.length + " frames (each frame is a StackTraceElement object when materialised)");
        if (sink == 42) System.out.println();
    }
}
