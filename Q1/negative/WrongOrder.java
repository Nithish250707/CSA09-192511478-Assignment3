import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Broader catch placed before a narrower one - the compiler rejects it. */
public class WrongOrder {
    public static void main(String[] args) {
        try {
            int a = new Scanner(System.in).nextInt();
            System.out.println(a);
        } catch (NoSuchElementException e) {          // broader (superclass)
            System.out.println("no input");
        } catch (InputMismatchException e) {          // narrower (subclass) - unreachable
            System.out.println("not a number");
        }
    }
}
