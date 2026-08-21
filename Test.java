import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Test {
    public static void main(String[] args) throws InterruptedException {
        // --- 1. Initialize Components ---
        InventoryManager inventoryManager = new InventoryManager();
        PaymentProcessor paymentProcessor = new PaymentProcessor();

        Product product = new Product(1, "Smartphone", 1000);
        int initialStock = 2000;
        inventoryManager.addProduct(product.getId(), initialStock);

        PricingStrategy discount = new PercentageDiscountStrategy(20);
        FlashSale flashSale = new FlashSale(discount, 0, 120000, 2);
        FlashSaleManager manager = FlashSaleManager.getInstance(inventoryManager, paymentProcessor, flashSale);

        int consumerCount = 40;
        int numUsers = 1200;

        ExecutorService buyerPool = Executors.newFixedThreadPool(50);
        ExecutorService consumerPool = Executors.newFixedThreadPool(consumerCount);

        AtomicInteger successfulOrders = new AtomicInteger(0);
        AtomicInteger failedPaymentOrders = new AtomicInteger(0);
        AtomicInteger rejectedRequests = new AtomicInteger(0);

        // --- 2. Start Background Payment Workers ---
        // Simplified worker loop: No sleep, zero CPU spin-wait
        for (int i = 0; i < consumerCount; i++) {
            consumerPool.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        manager.processNextOrder(); // Blocks natively until an order arrives
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Clean exit on shutdownNow()
                }
            });
        }

        // --- 3. Run Buyer Threads with Precision Timers ---
        int totalRequests = 2500;
        CountDownLatch latch = new CountDownLatch(totalRequests);

        long globalStartNano = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            final int userId = (i % numUsers) + 1;
                buyerPool.submit(() -> {
                    try {
                        Order order = manager.checkoutItem(userId, product, 1);
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

        // Wait for all buyer threads to finish receiving final confirmations
        latch.await();
        buyerPool.shutdown();

// Stop payment worker pool
        consumerPool.shutdownNow();
        consumerPool.awaitTermination(2, TimeUnit.SECONDS);

        long globalEndNano = System.nanoTime();
        double totalDurationSeconds = (globalEndNano - globalStartNano) / 1_000_000_000.0;
        long totalDurationMs = (globalEndNano - globalStartNano) / 1_000_000;

        int remainingStock = inventoryManager.getAvailableQuantity(product.getId());

        // --- Efficiency Metrics Calculations ---
        int totalProcessedOrders = successfulOrders.get() + failedPaymentOrders.get();
        double actualThroughput = totalProcessedOrders / totalDurationSeconds;
        
        // Ideal throughput: 40 workers / 0.075s average payment latency = 533.33 ops/sec
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
        System.out.println("========================================================");
    }
}