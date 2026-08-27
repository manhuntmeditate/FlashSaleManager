package com.flashsale.service;

import com.flashsale.Factory.PaymentMethodEnum;
import com.flashsale.model.Order;
import com.flashsale.model.OrderStatus;
import com.flashsale.model.Product;
import com.flashsale.observer.Notifier;
import com.flashsale.observer.OrderPublisher;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class FlashSaleManager {
    private static volatile FlashSaleManager instance;

    private final PaymentProcessor paymentProcessor;
    private final OrderPublisher orderPublisher;
    private final AtomicInteger orderIdCounter = new AtomicInteger(1);
    private final long startTimeStamp;

    private final Map<Integer, FlashSale> flashSales = new ConcurrentHashMap<>();
    private final BlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
    private final Map<Integer, Order> orders = new ConcurrentHashMap<>();

    private FlashSaleManager(PaymentProcessor paymentProcessor, OrderPublisher orderPublisher) {
        this.paymentProcessor = paymentProcessor;
        this.orderPublisher = orderPublisher;
        this.startTimeStamp = System.currentTimeMillis();
    }

    public static FlashSaleManager getInstance(PaymentProcessor pay, OrderPublisher orderPublisher) {
        if (instance == null) {
            synchronized (FlashSaleManager.class) {
                if (instance == null) {
                    instance = new FlashSaleManager(pay, orderPublisher);
                }
            }
        }
        return instance;
    }

    public void registerFlashSale(FlashSale flashSale) {
        flashSales.put(flashSale.getSaleId(), flashSale);
    }

    public FlashSale getFlashSale(int saleId) {
        return flashSales.get(saleId);
    }

    // ==========================================
    // ORCHESTRATION METHODS
    // ==========================================

    public Order checkoutItem(int saleId, int userId, Product product, int quantity, PaymentMethodEnum paymentMethod) {
        FlashSale sale = flashSales.get(saleId);
        if (sale == null) {
            return null;
        }

        // Delegate atomic validation, quota, and stock reservation to the target sale
        if (!sale.tryReserveQuotaAndStock(userId, product, quantity, getElapsedTime())) {
            return null;
        }

        int totalAmount = sale.calculatePrice(product, quantity);
        int orderId = orderIdCounter.getAndIncrement();

        Order order = new Order(orderId, saleId, userId, product.getId(), quantity, totalAmount, paymentMethod);
        orders.put(orderId, order);
        orderQueue.offer(order);

        return order;
    }

    public boolean processNextOrder() throws InterruptedException {
        Order order = orderQueue.take();

        boolean paid = paymentProcessor.processPayment(order);

        if (paid) {
            order.markSuccess();
        } else {
            order.markFailed();
            // Release stock and quota back to the specific sale
            FlashSale sale = flashSales.get(order.getSaleId());
            if (sale != null) {
                sale.releaseQuotaAndStock(order.getUserId(), order.getProductId(), order.getQuantity());
            }
        }
        orderPublisher.notifyObservers(order);

        return true;
    }

    public boolean refundOrder(int orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            return false;
        }

        boolean refunded = order.refund();
        if (refunded) {
            FlashSale sale = flashSales.get(order.getSaleId());
            if (sale != null) {
                sale.releaseQuotaAndStock(order.getUserId(), order.getProductId(), order.getQuantity());
            }
            orderPublisher.notifyObservers(order);
        }
        return refunded;
    }

    public void registerNotifier(Notifier notifier) {
        this.orderPublisher.addObserver(notifier);
    }

    // ==========================================
    // STATUS & METRICS
    // ==========================================

    public OrderStatus getOrderStatus(int orderId) {
        Order order = orders.get(orderId);
        return (order == null) ? OrderStatus.NOT_FOUND : order.getStatus();
    }

    public BlockingQueue<Order> getOrderQueue() {
        return orderQueue;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTimeStamp;
    }

    public Collection<Order> getAllOrders() {
        return orders.values();
    }
}