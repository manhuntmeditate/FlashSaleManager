package com.flashsale;

import com.flashsale.Factory.*;
import com.flashsale.model.*;
import com.flashsale.strategy.*;
import com.flashsale.observer.*;
import com.flashsale.service.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Test {
    public static void main(String[] args) throws InterruptedException {
        // --- 1. Shared Infrastructure ---
        PaymentProcessor paymentProcessor = new PaymentProcessor();
        OrderPublisher orderPublisher = new OrderPublisher();
        AnalyticsNotifier analyticsNotifier = new AnalyticsNotifier();
        EmailNotifier emailNotifier = new EmailNotifier();

        orderPublisher.addObserver(analyticsNotifier);
        orderPublisher.addObserver(emailNotifier);

        FlashSaleManager manager = FlashSaleManager.getInstance(paymentProcessor, orderPublisher);

        // --- 2. Master Product Catalog (8 Unique Products) ---
        Product[] catalog = new Product[] {
            new Product(1, "Smartphone", 1000),
            new Product(2, "Wireless Headphones", 200),
            new Product(3, "Smart Watch", 350),
            new Product(4, "Mechanical Keyboard", 150),
            new Product(5, "Gaming Mouse", 80),
            new Product(6, "4K Monitor", 500),
            new Product(7, "USB-C Hub", 45),
            new Product(8, "Portable SSD", 120)
        };

        // --- 3. Configure 5 Independent Flash Sales ---
        // 5 sales * 8 products * 50 units = 2000 total initial stock
        int numSales = 5;
        int stockPerItem = 50;
        FlashSale[] registeredSales = new FlashSale[numSales];
        Map<Integer, Product[]> saleProductMap = new HashMap<>();

        PricingStrategy[] strategies = new PricingStrategy[] {
            new PercentageDiscountStrategy(10),
            new PercentageDiscountStrategy(20),
            new PercentageDiscountStrategy(30),
            new FlatDiscountStrategy(25),
            new FlatDiscountStrategy(50)
        };

        int totalInitialStock = 0;

        for (int s = 1; s <= numSales; s++) {
            InventoryManager saleInv = new InventoryManager();
            for (Product p : catalog) {
                saleInv.addProduct(p.getId(), stockPerItem);
                totalInitialStock += stockPerItem;
            }

            FlashSale sale = new FlashSale(s, strategies[s - 1], 0, 120000, 2, saleInv);
            manager.registerFlashSale(sale);
            registeredSales[s - 1] = sale;
            saleProductMap.put(s, catalog);
        }

        int consumerCount = 40;
        int numUsers = 1200;

        ExecutorService buyerPool = Executors.newFixedThreadPool(50);
        ExecutorService consumerPool = Executors.newFixedThreadPool(consumerCount);

        AtomicInteger successfulOrders = new AtomicInteger(0);
        AtomicInteger failedPaymentOrders = new AtomicInteger(0);
        AtomicInteger rejectedRequests = new AtomicInteger(0);

        // --- 4. Start Background Payment Consumers ---
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

        // --- 5. High-Concurrency Traffic Simulation ---
        int totalRequests = 2500;
        CountDownLatch latch = new CountDownLatch(totalRequests);
        PaymentMethodEnum[] availableMethods = PaymentMethodEnum.values();
        Random random = new Random();

        long globalStartNano = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            final int userId = (i % numUsers) + 1;
            buyerPool.submit(() -> {
                try {
                    FlashSale selectedSale = registeredSales[random.nextInt(registeredSales.length)];
                    Product[] products = saleProductMap.get(selectedSale.getSaleId());
                    Product selectedProduct = products[random.nextInt(products.length)];
                    PaymentMethodEnum selectedMethod = availableMethods[random.nextInt(availableMethods.length)];

                    Order order = manager.checkoutItem(selectedSale.getSaleId(), userId, selectedProduct, 1, selectedMethod);
                    if (order == null) {
                        rejectedRequests.incrementAndGet();
                    } else {
                        OrderStatus finalStatus = order.getFuture().get();
                        if (finalStatus == OrderStatus.SUCCESS) {
                            successfulOrders.incrementAndGet();
                        } else if (finalStatus == OrderStatus.FAILED) {
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

        int totalStockAfterSale = 0;
        for (FlashSale sale : registeredSales) {
            for (Product p : catalog) {
                totalStockAfterSale += sale.getInventoryManager().getAvailableQuantity(p.getId());
            }
        }

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
        System.out.println("Total Initial Stock:           " + totalInitialStock);
        System.out.println("Total Stock After Sale:        " + totalStockAfterSale);
        System.out.println("Total Stock Consumed:          " + (totalInitialStock - totalStockAfterSale));
        System.out.println("--------------------------------------------------------");
        System.out.println("--- Per-Sale & Per-Product Stock Breakdown (Pre-Refund) ---");
        
        for (FlashSale sale : registeredSales) {
            int saleRemaining = 0;
            System.out.printf(">> Sale ID %d:\n", sale.getSaleId());
            for (Product p : catalog) {
                int left = sale.getInventoryManager().getAvailableQuantity(p.getId());
                saleRemaining += left;
                System.out.printf("   [%d] %-20s : %3d / %d left\n", p.getId(), p.getName(), left, stockPerItem);
            }
            System.out.printf("   >> Subtotal for Sale %d  : %3d / %d remaining\n\n", 
                sale.getSaleId(), saleRemaining, catalog.length * stockPerItem);
        }
        
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
            if (i % 20 == 0) {
                if (order.getStatus() == OrderStatus.SUCCESS) {
                    validRefundsAttempted++;
                    boolean refunded = manager.refundOrder(order.getOrderId());
                    if (refunded) {
                        validRefundsSuccessful++;
                        falseRefundsAttempted++;
                        boolean doubleRefund = manager.refundOrder(order.getOrderId());
                        if (!doubleRefund) {
                            falseRefundsBlocked++;
                        }
                    }
                } else if (order.getStatus() == OrderStatus.FAILED) {
                    falseRefundsAttempted++;
                    boolean failedOrderRefund = manager.refundOrder(order.getOrderId());
                    if (!failedOrderRefund) {
                        falseRefundsBlocked++;
                    }
                }
            }
        }

        falseRefundsAttempted++;
        if (!manager.refundOrder(999999)) {
            falseRefundsBlocked++;
        }

        int finalTotalRemainingStock = 0;
        for (FlashSale sale : registeredSales) {
            for (Product p : catalog) {
                finalTotalRemainingStock += sale.getInventoryManager().getAvailableQuantity(p.getId());
            }
        }

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
        System.out.println("Total Initial Stock:           " + totalInitialStock);
        System.out.println("Final Total Remaining Stock:   " + finalTotalRemainingStock);
        System.out.println("Net Total Stock Consumed:      " + (totalInitialStock - finalTotalRemainingStock));
        System.out.println("Total Restocked via Refunds:   " + (finalTotalRemainingStock - totalStockAfterSale));
        System.out.println("--------------------------------------------------------");
        System.out.println("--- Per-Sale & Per-Product Stock Breakdown (Post-Refund) ---");
        
        for (FlashSale sale : registeredSales) {
            int saleRemaining = 0;
            System.out.printf(">> Sale ID %d:\n", sale.getSaleId());
            for (Product p : catalog) {
                int left = sale.getInventoryManager().getAvailableQuantity(p.getId());
                saleRemaining += left;
                System.out.printf("   [%d] %-20s : %3d / %d left\n", p.getId(), p.getName(), left, stockPerItem);
            }
            System.out.printf("   >> Subtotal for Sale %d  : %3d / %d remaining\n\n", 
                sale.getSaleId(), saleRemaining, catalog.length * stockPerItem);
        }
        
        System.out.println("--------------------------------------------------------");
        System.out.println("[Observer] Post-Refund Total Orders:  " + analyticsNotifier.getTotalOrdersProcessed());
        System.out.println("[Observer] Post-Refund Active Success:" + analyticsNotifier.getSuccessfulOrders());
        System.out.println("[Observer] Post-Refund Refund Count:  " + analyticsNotifier.getRefundedOrders());
        System.out.println("[Observer] Post-Refund Failed Orders: " + analyticsNotifier.getFailedOrders());
        System.out.println("[Observer] Post-Refund Net Revenue:  $" + analyticsNotifier.getTotalRevenue());
        System.out.println("========================================================");
    }
}