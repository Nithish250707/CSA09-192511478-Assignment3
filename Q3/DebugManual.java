import java.util.List;

/** Driver used for the jdb step-through session (debug_session.txt). */
public class DebugManual {
    public static void main(String[] args) {
        boolean ok = ReportGenerators.manual(List.of("stores_bad/store1.csv", "stores_bad/store3.csv"), "out/debug_report.csv");
        System.out.println("manual() returned " + ok + ", open fds = " + ReportGenerators.openFds());
    }
}
