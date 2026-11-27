import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/** Storage layer: the only place that knows about files and IOException. */
class SubmissionStorage {
    static final long LIMIT_KB = 500;
    void store(String regNo, String file, long kb, boolean keepCause) throws AnswerFileTooLargeException, SubmissionStorageException {
        if (kb > LIMIT_KB) throw new AnswerFileTooLargeException(file + " is " + kb + " KB; limit is " + LIMIT_KB + " KB");
        try {
            Disk.write(regNo + "/" + file, kb);
        } catch (IOException e) {
            if (keepCause) throw new SubmissionStorageException("Could not store " + file + " for " + regNo, e);   // chained
            throw new SubmissionStorageException("Could not store " + file + " for " + regNo, null);               // cause LOST
        }
    }
}

/** Service layer: business rule "student must be registered". Propagates storage exceptions with throws. */
class SubmissionService {
    private final Map<String, Set<String>> registrations = Map.of("192511478", Set.of("CSA09", "CSA04"), "192511001", Set.of("CSA04"));
    private final SubmissionStorage storage = new SubmissionStorage();
    void submit(String regNo, String course, String file, long kb, boolean keepCause) throws ExamException {
        if (regNo == null || regNo.isBlank()) throw new InvalidSubmissionException("regNo must not be blank");
        if (!registrations.getOrDefault(regNo, Set.of()).contains(course))
            throw new NotRegisteredException(regNo + " is not registered for " + course);
        storage.store(regNo, file, kb, keepCause);        // may throw - not caught here, declared via throws ExamException
    }
}

/** Controller layer: checks the exam window, then is the ONE place that turns exceptions into a user message. */
class SubmissionController {
    private final SubmissionService service = new SubmissionService();
    private final LocalDateTime windowEnd;
    SubmissionController(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }

    String handle(String regNo, String course, String file, long kb, LocalDateTime now, boolean keepCause) {
        try {
            if (now.isAfter(windowEnd)) throw new ExamWindowClosedException("Exam window closed at " + windowEnd.toLocalTime());
            service.submit(regNo, course, file, kb, keepCause);
            return "ACCEPTED: " + file + " submitted.";
        } catch (ExamWindowClosedException e) {
            return "REJECTED: " + e.getMessage() + ". Late submissions are not accepted.";
        } catch (NotRegisteredException e) {
            return "REJECTED: " + e.getMessage() + ". Contact the exam cell.";
        } catch (AnswerFileTooLargeException e) {
            return "REJECTED: " + e.getMessage() + ". Compress the file and resubmit.";
        } catch (SubmissionStorageException e) {
            log(e);                                        // full chain for engineers, polite message for students
            return "REJECTED: Server could not save your answer. It has NOT been submitted; please retry. (ref " + Integer.toHexString(System.identityHashCode(e)) + ")";
        } catch (ExamException e) {
            return "REJECTED: " + e.getMessage();
        } catch (InvalidSubmissionException e) {
            log(e);
            return "REJECTED: invalid request (" + e.getMessage() + ")";
        }
    }
    static void log(Throwable e) {
        System.out.println("   [server log] " + e);
        for (Throwable c = e.getCause(); c != null; c = c.getCause()) System.out.println("   [server log]   caused by: " + c);
        if (e.getCause() == null && e instanceof SubmissionStorageException) System.out.println("   [server log]   caused by: <none - root cause lost>");
    }
}
