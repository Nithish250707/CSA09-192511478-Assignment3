/** Same mistake with Exception first. */
public class CatchAllFirst {
    public static void main(String[] args) {
        try {
            Integer.parseInt(args[0]);
        } catch (Exception e) {
            System.out.println("something failed");
        } catch (NumberFormatException e) {
            System.out.println("bad number");
        }
    }
}
