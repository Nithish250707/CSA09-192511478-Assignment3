import java.io.IOException;

/** Root of the portal's checked exceptions: every rejection the student must be told about. */
class ExamException extends Exception {
    ExamException(String message) { super(message); }
    ExamException(String message, Throwable cause) { super(message, cause); }
}
/** Rule broken in the controller layer: submission after the exam window. */
class ExamWindowClosedException extends ExamException {
    ExamWindowClosedException(String msg) { super(msg); }
}
/** Rule broken in the service layer: student not registered for the course. */
class NotRegisteredException extends ExamException {
    NotRegisteredException(String msg) { super(msg); }
}
/** Rule broken in the storage layer: file exceeds the size limit. */
class AnswerFileTooLargeException extends ExamException {
    AnswerFileTooLargeException(String msg) { super(msg); }
}
/** Storage infrastructure failure, wrapping the low-level cause (IOException). */
class SubmissionStorageException extends ExamException {
    SubmissionStorageException(String msg, Throwable cause) { super(msg, cause); }
}
/** Unchecked: programming errors (null/blank arguments) - caller bugs, not user mistakes. */
class InvalidSubmissionException extends RuntimeException {
    InvalidSubmissionException(String msg) { super(msg); }
}
/** Simulates the disk. */
class Disk {
    static boolean full = false;
    static void write(String name, long kb) throws IOException {
        if (full) throw new IOException("No space left on device (/exam-store)");
    }
}
