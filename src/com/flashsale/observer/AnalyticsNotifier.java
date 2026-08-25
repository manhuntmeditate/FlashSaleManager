package com.flashsale.observer;
import com.flashsale.model.OrderStatus;
import com.flashsale.model.Order;
import java.util.concurrent.atomic.AtomicInteger;

public class AnalyticsNotifier implements Notifier {
    private final AtomicInteger totalOrdersProcessed = new AtomicInteger(0);
    private final AtomicInteger successfulOrders = new AtomicInteger(0);
    private final AtomicInteger failedOrders = new AtomicInteger(0);
    private final AtomicInteger refundedOrders = new AtomicInteger(0); // Added for refund tracking
    private final AtomicInteger totalRevenue = new AtomicInteger(0);

    @Override
    public void update(Order order) {
        OrderStatus status = order.getStatus();

        if (OrderStatus.SUCCESS == status) {
            totalOrdersProcessed.incrementAndGet();
            successfulOrders.incrementAndGet();
            totalRevenue.addAndGet(order.getAmount());
        } else if (OrderStatus.FAILED == status) {
            totalOrdersProcessed.incrementAndGet();
            failedOrders.incrementAndGet();
        } else if (OrderStatus.REFUNDED == status) {
            refundedOrders.incrementAndGet();
            successfulOrders.decrementAndGet();
            totalRevenue.addAndGet(-order.getAmount());
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

    public int getRefundedOrders() {
        return refundedOrders.get();
    }

    public int getTotalRevenue() {
        return totalRevenue.get();
    }
}