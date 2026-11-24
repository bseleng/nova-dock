package local.nova.dock;

public final class StableConnectionTest {
    private static void check(Object expected,Object actual) {
        if (expected==null ? actual!=null : !expected.equals(actual)) throw new AssertionError("Expected "+expected+", got "+actual);
    }
    public static void main(String[] args) {
        StableConnection d=new StableConnection();
        check(null,d.update(false,0)); check(null,d.update(false,2199)); check(false,d.update(false,2200));
        check(null,d.update(true,3000)); check(null,d.update(true,4199)); check(true,d.update(true,4200));
        // A brief link dropout must not produce a portable profile.
        check(null,d.update(false,4500)); check(null,d.update(true,4700)); check(true,d.update(true,5900));
        // Loss of read permission must not be mistaken for removing the display.
        check(null,d.update(null,6000)); check(null,d.update(false,10000)); check(null,d.update(null,11000));
        check(null,d.update(false,12000)); check(false,d.update(false,14200));
        System.out.println("PASS: connection settling, brief dropout, unknown state, reconnect");
    }
}
