public class PaymentProcessor {

    public boolean processPayment(int orderId, int amount) {
        // Wait between 0.05s (50ms) and 0.55s (100ms)
        try {
            long delay = 50 + (long) (Math.random() * 50);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            return false;
        }

        // 90% chance of success (true), 10% chance of failure (false)
        return Math.random() < 0.90;
    }
}
