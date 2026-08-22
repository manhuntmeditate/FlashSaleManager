import java.util.concurrent.atomic.AtomicInteger;

public class AnalyticsNotifier implements Notifier {
    private final AtomicInteger totalOrdersProcessed = new AtomicInteger(0);
    private final AtomicInteger successfulOrders = new AtomicInteger(0);
    private final AtomicInteger failedOrders = new AtomicInteger(0);
    private final AtomicInteger totalRevenue = new AtomicInteger(0);

    @Override
    public void update(Order order) {
        totalOrdersProcessed.incrementAndGet();

        if (order.getStatus() == "SUCCESS") {
            successfulOrders.incrementAndGet();
            totalRevenue.addAndGet(order.getAmount());
        } else if (order.getStatus() == "FAILED") {
            failedOrders.incrementAndGet();
        }
    }

    public int getTotalOrdersProcessed() {
        return totalOrdersProcessed.get();
    }

    public int getSuccessfulOrders() {
        return successfulOrders.get();
    }

    public int getFailedOrders() {
        return failedOrders.get();
    }

    public int getTotalRevenue() {
        return totalRevenue.get();
    }
}