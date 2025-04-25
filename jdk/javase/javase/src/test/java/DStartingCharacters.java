import java.util.concurrent.locks.LockSupport;

public class DStartingCharacters {

    public static void main(String[] args) throws Exception {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(100000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println(Thread.currentThread().isInterrupted());
                throw new RuntimeException(e);
            }
        });
        thread.start();
        thread.join();
    }
}