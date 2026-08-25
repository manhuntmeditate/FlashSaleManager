package com.flashsale;

import com.flashsale.Factory.*;
import com.flashsale.model.*;
import com.flashsale.strategy.*;
import com.flashsale.observer.*;
import com.flashsale.service.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Test {
    public static void main(String[] args) throws InterruptedException {
        // --- 1. Initialize Components & Observers ---
        InventoryManager inventoryManager = new InventoryManager();
        PaymentProcessor paymentProcessor = new PaymentProcessor();

        Product product = new Product(1, "Smartphone", 1000);
        int initialStock = 2000;
        inventoryManager.addProduct(product.getId(), initialStock);

        PricingStrategy discount = new PercentageDiscountStrategy(20);
        FlashSale flashSale = new FlashSale(discount, 0, 120000, 2);
        
        // Setup Observers
        OrderPublisher orderPublisher = new OrderPublisher();
        AnalyticsNotifier analyticsNotifier = new AnalyticsNotifier();
        EmailNotifier emailNotifier = new EmailNotifier();

        orderPublisher.addObserver(analyticsNotifier);
        orderPublisher.addObserver(emailNotifier);

        FlashSaleManager manager = FlashSaleManager.getInstance(inventoryManager, paymentProcessor, flashSale, orderPublisher);

        int consumerCount = 40;
        int numUsers = 1200;

        ExecutorService buyerPool = Executors.newFixedThreadPool(50);
        ExecutorService consumerPool = Executors.newFixedThreadPool(consumerCount);

        AtomicInteger successfulOrders = new AtomicInteger(0);
        AtomicInteger failedPaymentOrders = new AtomicInteger(0);
        AtomicInteger rejectedRequests = new AtomicInteger(0);

        // --- 2. Start Background Payment Workers ---
        for (int i = 0; i < consumerCount; i++) {
            consumerPool.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        manager.processNextOrder();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // --- 3. Run Buyer Threads with Payment Selection ---
        int totalRequests = 2500;
        CountDownLatch latch = new CountDownLatch(totalRequests);
        PaymentMethodEnum[] availableMethods = PaymentMethodEnum.values();
        Random random = new Random();

        long globalStartNano = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            final int userId = (i % numUsers) + 1;
            buyerPool.submit(() -> {
                try {
                    PaymentMethodEnum selectedMethod = availableMethods[random.nextInt(availableMethods.length)];

                    Order order = manager.checkoutItem(userId, product, 1, selectedMethod);
                    if (order == null) {
                        rejectedRequests.incrementAndGet();
                    } else {
                        String finalStatus = order.getFuture().get(); 

                        if ("SUCCESS".equals(finalStatus)) {
                            successfulOrders.incrementAndGet();
                        } else if ("FAILED".equals(finalStatus)) {
                            failedPaymentOrders.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        buyerPool.shutdown();

        consumerPool.shutdownNow();
        consumerPool.awaitTermination(2, TimeUnit.SECONDS);

        long globalEndNano = System.nanoTime();
        double totalDurationSeconds = (globalEndNano - globalStartNano) / 1_000_000_000.0;
        long totalDurationMs = (globalEndNano - globalStartNano) / 1_000_000;

        int stockAfterSale = inventoryManager.getAvailableQuantity(product.getId());

        // --- Efficiency Metrics Calculations ---
        int totalProcessedOrders = successfulOrders.get() + failedPaymentOrders.get();
        double actualThroughput = totalProcessedOrders / totalDurationSeconds;
        double avgPaymentLatencySeconds = 0.075; 
        double idealThroughput = consumerCount / avgPaymentLatencySeconds;
        double systemEfficiency = (actualThroughput / idealThroughput) * 100.0;

        // =========================================================================
        // PART 1: METRICS BEFORE REFUNDS
        // =========================================================================
        System.out.println("\n========================================================");
        System.out.println("========== PART 1: METRICS BEFORE REFUNDS ==============");
        System.out.println("========================================================");
        System.out.printf("Total Sale Runtime: %d ms (%.2f seconds)\n", totalDurationMs, totalDurationSeconds);
        System.out.printf("Request Throughput: %.2f requests/sec\n", totalRequests / totalDurationSeconds);
        System.out.printf("Payment Processing Throughput: %.2f orders/sec\n", actualThroughput);
        System.out.printf("System Efficiency: %.2f%%\n", systemEfficiency);
        System.out.println("--------------------------------------------------------");
        System.out.println("Total Checkout Requests:       " + totalRequests);
        System.out.println("Confirmed SUCCESS Orders:      " + successfulOrders.get());
        System.out.println("Confirmed FAILED Orders:       " + failedPaymentOrders.get());
        System.out.println("Rejected Requests:             " + rejectedRequests.get());
        System.out.println("Initial Stock:                 " + initialStock);
        System.out.println("Stock Remaining After Sale:    " + stockAfterSale);
        System.out.println("Stock Consumed:                " + (initialStock - stockAfterSale));
        System.out.println("--------------------------------------------------------");
        System.out.println("[Observer] Pre-Refund Revenue: $" + analyticsNotifier.getTotalRevenue());
        System.out.println("[Observer] Pre-Refund Success: " + analyticsNotifier.getSuccessfulOrders());
        System.out.println("========================================================\n");

        // =========================================================================
        // PART 2: REFUND EXECUTION & FALSE REFUND VERIFICATION
        // =========================================================================
        List<Order> allOrders = new ArrayList<>(manager.getAllOrders());
        int validRefundsAttempted = 0;
        int validRefundsSuccessful = 0;
        int falseRefundsAttempted = 0;
        int falseRefundsBlocked = 0;

        for (int i = 0; i < allOrders.size(); i++) {
            Order order = allOrders.get(i);

            // Target roughly 5% sample for refund attempts (every 20th order)
            if (i % 20 == 0) {
                if ("SUCCESS".equals(order.getStatus())) {
                    validRefundsAttempted++;
                    boolean refunded = manager.refundOrder(order.getOrderId());
                    if (refunded) {
                        validRefundsSuccessful++;
                        // Immediate Double-Refund Test: Trying to refund an already refunded order should fail
                        falseRefundsAttempted++;
                        boolean doubleRefund = manager.refundOrder(order.getOrderId());
                        if (!doubleRefund) {
                            falseRefundsBlocked++;
                        }
                    }
                } else if ("FAILED".equals(order.getStatus())) {
                    // False Refund Test: Attempting to refund an order that failed payment must be blocked
                    falseRefundsAttempted++;
                    boolean failedOrderRefund = manager.refundOrder(order.getOrderId());
                    if (!failedOrderRefund) {
                        falseRefundsBlocked++;
                    }
                }
            }
        }

        // False Refund Test on non-existent order ID
        falseRefundsAttempted++;
        if (!manager.refundOrder(999999)) {
            falseRefundsBlocked++;
        }

        int finalRemainingStock = inventoryManager.getAvailableQuantity(product.getId());

        // =========================================================================
        // PART 3: METRICS AFTER REFUNDS
        // =========================================================================
        System.out.println("========================================================");
        System.out.println("========== PART 2: METRICS AFTER REFUNDS ===============");
        System.out.println("========================================================");
        System.out.println("Valid Refunds Attempted:       " + validRefundsAttempted);
        System.out.println("Valid Refunds Succeeded:       " + validRefundsSuccessful);
        System.out.println("False Refunds Attempted:       " + falseRefundsAttempted);
        System.out.println("False Refunds Blocked by State:" + falseRefundsBlocked);
        System.out.println("--------------------------------------------------------");
        System.out.println("Initial Stock:                 " + initialStock);
        System.out.println("Final Remaining Stock:         " + finalRemainingStock);
        System.out.println("Net Stock Consumed:            " + (initialStock - finalRemainingStock));
        System.out.println("Restocked Quantity via Refund: " + (finalRemainingStock - stockAfterSale));
        System.out.println("--------------------------------------------------------");
        System.out.println("[Observer] Post-Refund Total Orders:  " + analyticsNotifier.getTotalOrdersProcessed());
        System.out.println("[Observer] Post-Refund Active Success:" + analyticsNotifier.getSuccessfulOrders());
        System.out.println("[Observer] Post-Refund Refund Count:  " + analyticsNotifier.getRefundedOrders());
        System.out.println("[Observer] Post-Refund Failed Orders: " + analyticsNotifier.getFailedOrders());
        System.out.println("[Observer] Post-Refund Net Revenue:  $" + analyticsNotifier.getTotalRevenue());
        System.out.println("========================================================");
    }
}