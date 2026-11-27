import java.time.LocalDateTime;

public class PortalDemo {
    public static void main(String[] args) throws Exception {
        LocalDateTime end = LocalDateTime.of(2026, 11, 27, 12, 0);
        LocalDateTime inTime = end.minusMinutes(30), late = end.plusMinutes(5);
        SubmissionController c = new SubmissionController(end);

        System.out.println("| # | Scenario | Exception expected | Message shown to student |");
        System.out.println("|---|---|---|---|");
        row(1, "valid submission", "none", c.handle("192511478", "CSA09", "ans.pdf", 120, inTime, true));
        row(2, "after window", "ExamWindowClosedException", c.handle("192511478", "CSA09", "ans.pdf", 120, late, true));
        row(3, "not registered", "NotRegisteredException", c.handle("192511001", "CSA09", "ans.pdf", 120, inTime, true));
        row(4, "file 800 KB", "AnswerFileTooLargeException", c.handle("192511478", "CSA09", "big.pdf", 800, inTime, true));
        Disk.full = true;
        row(5, "disk full (chained)", "SubmissionStorageException <- IOException", c.handle("192511478", "CSA09", "ans.pdf", 120, inTime, true));
        row(6, "disk full, cause NOT passed", "SubmissionStorageException (no cause)", c.handle("192511478", "CSA09", "ans.pdf", 120, inTime, false));
        Disk.full = false;
        row(7, "blank regNo (caller bug)", "InvalidSubmissionException (unchecked)", c.handle(" ", "CSA09", "ans.pdf", 120, inTime, true));

        System.out.println();
        System.out.println("=== Full chained stack trace (scenario 5, printed by printStackTrace) ===");
        Disk.full = true;
        try { new SubmissionService().submit("192511478", "CSA09", "ans.pdf", 120, true); }
        catch (ExamException e) {
            e.printStackTrace(System.out);
            System.out.println("getCause() = " + e.getCause());
            System.out.println("getCause().getMessage() = " + e.getCause().getMessage());
        }
        System.out.println();
        System.out.println("=== Same failure re-thrown WITHOUT its cause ===");
        try { new SubmissionService().submit("192511478", "CSA09", "ans.pdf", 120, false); }
        catch (ExamException e) {
            e.printStackTrace(System.out);
            System.out.println("getCause() = " + e.getCause());
        }

        System.out.println();
        System.out.println("=== Cost of capturing stack traces (alternative concept) ===");
        int n = 200_000; long sink = 0;
        for (int depth : new int[]{1, 10, 50}) {
            for (int rep = 0; rep < 2; rep++) {
                long t0 = System.nanoTime();
                for (int i = 0; i < n; i++) sink += deep(depth, false).hashCode() & 1;
                long t1 = System.nanoTime();
                for (int i = 0; i < n; i++) sink += deep(depth, true).hashCode() & 1;
                long t2 = System.nanoTime();
                if (rep == 1) System.out.printf("depth %2d: new exception %.0f ns | new chained exception (cause + wrapper) %.0f ns | frames recorded %d%n",
                        depth, (t1 - t0) / (double) n, (t2 - t1) / (double) n, deep(depth, false).getStackTrace().length);
            }
        }
        if (sink == -1) System.out.println();
    }
    static Exception deep(int d, boolean chained) {
        if (d > 1) return deep(d - 1, chained);
        if (!chained) return new ExamException("x");
        return new SubmissionStorageException("wrap", new java.io.IOException("cause"));
    }
    static void row(int n, String s, String exp, String msg) { System.out.println("| " + n + " | " + s + " | " + exp + " | " + msg + " |"); }
}
