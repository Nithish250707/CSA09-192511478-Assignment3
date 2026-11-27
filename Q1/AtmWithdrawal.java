import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Checked, user-defined: the caller can recover (show balance, ask for a smaller amount). */
class InsufficientFundsException extends Exception {
    InsufficientFundsException(String msg) { super(msg); }
}

/**
 * Q1 - ATM withdrawal. The card reader is a file holding the account number;
 * the amount is typed by the user (standard input).
 * Usage: java AtmWithdrawal <cardReaderFile>  < typedInput
 */
public class AtmWithdrawal {
    private static final Map<Long, Double> balances = new HashMap<>();
    static { balances.put(1001L, 5000.0); balances.put(1002L, 300.0); }

    static long readCard(String path) throws IOException {             // checked: propagated to caller
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return Long.parseLong(r.readLine().trim());                 // NumberFormatException (unchecked)
        }
    }

    static double withdraw(long account, double amount) throws InsufficientFundsException {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive: " + amount);
        if (amount % 100 != 0) throw new IllegalArgumentException("ATM dispenses multiples of Rs 100 only");
        Double bal = balances.get(account);
        double balance = bal;                                           // NullPointerException if unknown account (deliberately NOT caught)
        if (amount > balance) throw new InsufficientFundsException("requested Rs " + amount + " but balance is Rs " + balance);
        balances.put(account, balance - amount);
        return balance - amount;
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String path = args.length > 0 ? args[0] : "card.txt";
        try {
            long account = readCard(path);
            System.out.println("Card read: account " + account);
            System.out.print("Enter amount: ");
            double amount = in.nextInt();                               // InputMismatchException for letters
            double left = withdraw(account, amount);
            System.out.println("\nDispensed Rs " + amount + ". Remaining balance Rs " + left);
        } catch (FileNotFoundException e) {                             // narrower checked first
            System.out.println("[catch FileNotFoundException] Card reader file missing: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("[catch IOException] Card reader failure: " + e.getMessage());
        } catch (InputMismatchException | NumberFormatException e) {   // multi-catch: bad numeric input
            System.out.println("\n[catch InputMismatchException|NumberFormatException] Not a number: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " - " + e.getMessage()));
        } catch (NoSuchElementException e) {                            // must come AFTER InputMismatchException (its subclass)
            System.out.println("\n[catch NoSuchElementException] No amount entered");
        } catch (IllegalArgumentException e) {
            System.out.println("\n[catch IllegalArgumentException] Invalid amount: " + e.getMessage());
        } catch (InsufficientFundsException e) {
            System.out.println("\n[catch InsufficientFundsException] " + e.getMessage());
        } finally {
            in.close();
            System.out.println("[finally] Card ejected, session closed");
        }
    }
}
