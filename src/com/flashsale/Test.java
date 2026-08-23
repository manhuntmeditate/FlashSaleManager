package com.flashsale;

import com.flashsale.Factory.*;
import com.flashsale.model.*;
import com.flashsale.strategy.*;
import com.flashsale.observer.*;
import com.flashsale.service.*;

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
                    // Randomly select UPI or CREDIT_CARD
                    PaymentMethodEnum selectedMethod = availableMethods[random.nextInt(availableMethods.length)];

                    Order order = manager.checkoutItem(userId, product, 1, selectedMethod);
                    if (order == null) {
                        rejectedRequests.incrementAndGet();
                    } else {
                        // Block cleanly until the consumer worker resolves the payment
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

        int remainingStock = inventoryManager.getAvailableQuantity(product.getId());

        // --- Efficiency Metrics Calculations ---
        int totalProcessedOrders = successfulOrders.get() + failedPaymentOrders.get();
        double actualThroughput = totalProcessedOrders / totalDurationSeconds;
        double avgPaymentLatencySeconds = 0.075; 
        double idealThroughput = consumerCount / avgPaymentLatencySeconds;
        double systemEfficiency = (actualThroughput / idealThroughput) * 100.0;

        // --- 4. Summary Output ---
        System.out.println("\n========== REAL-TIME STATUS CONFIRMATION TEST ==========");
        System.out.printf("Total Runtime: %d ms (%.2f seconds)\n", totalDurationMs, totalDurationSeconds);
        System.out.printf("Request Throughput: %.2f requests/sec\n", totalRequests / totalDurationSeconds);
        System.out.printf("Payment Processing Throughput: %.2f orders/sec\n", actualThroughput);
        System.out.printf("Ideal Max Throughput: %.2f orders/sec\n", idealThroughput);
        System.out.printf("System Efficiency: %.2f%%\n", systemEfficiency);
        System.out.println("--------------------------------------------------------");
        System.out.println("Total Checkout Requests: " + totalRequests);
        System.out.println("Confirmed SUCCESS Orders: " + successfulOrders.get());
        System.out.println("Confirmed FAILED (Payment) Orders: " + failedPaymentOrders.get());
        System.out.println("Rejected Requests (Limit/Stock): " + rejectedRequests.get());
        System.out.println("Initial Stock: " + initialStock);
        System.out.println("Final Remaining Stock: " + remainingStock);
        System.out.println("Net Stock Consumed: " + (initialStock - remainingStock));
        System.out.println("--------------------------------------------------------");

        // --- 5. Payment Gateway Telemetry Audit ---
        System.out.println("============== PAYMENT GATEWAY AUDIT ==============");
        for (PaymentMethodEnum method : PaymentMethodEnum.values()) {
            PaymentGateway gw = method.getGateway();
            System.out.printf("[%s] Passed: %d | Failed: %d | Total: %d%n",
                method.name(),
                gw.getSuccessCount(),
                gw.getFailureCount(),
                gw.getSuccessCount() + gw.getFailureCount()
            );
        }
        System.out.println("--------------------------------------------------------");
        
        // --- 6. Observer Analytics Output ---
        System.out.println("=========== OBSERVER (ANALYTICS) METRICS ===========");
        System.out.println("Analytics Total Orders Received: " + analyticsNotifier.getTotalOrdersProcessed());
        System.out.println("Analytics Successful Orders:     " + analyticsNotifier.getSuccessfulOrders());
        System.out.println("Analytics Failed Orders:         " + analyticsNotifier.getFailedOrders());
        System.out.println("Analytics Total Revenue:        $" + analyticsNotifier.getTotalRevenue());
        System.out.println("========================================================");
    }
}