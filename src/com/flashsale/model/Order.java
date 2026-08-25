package com.flashsale.model;

import com.flashsale.State.OrderState;
import com.flashsale.State.PendingOrderState;
import com.flashsale.Factory.PaymentMethodEnum;
import java.util.concurrent.CompletableFuture;

public class Order {
    private final int orderId;
    private final int userId;
    private final int productId;
    private final int quantity;
    private final int amount;
    private final PaymentMethodEnum paymentMethod;
    private volatile OrderState state;
    
    // Future now resolves to OrderStatus enum
    private final CompletableFuture<OrderStatus> future = new CompletableFuture<>();

    public Order(int orderId, int userId, int productId, int quantity, int amount, PaymentMethodEnum paymentMethod) {
        this.orderId = orderId;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.state = new PendingOrderState();
    }

    public int getOrderId() { return orderId; }
    public int getUserId() { return userId; }
    public int getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public int getAmount() { return amount; }
    public PaymentMethodEnum getPaymentMethod() { return paymentMethod; }
    
    public OrderStatus getStatus() { 
        return state.getStatusName(); 
    }

    public synchronized void setState(OrderState state) {
        this.state = state;
        if (state.getStatusName() != OrderStatus.PENDING) {
            this.future.complete(state.getStatusName());
        }
    }

    public synchronized boolean markSuccess() {
        return state.markSuccess(this);
    }

    public synchronized boolean markFailed() {
        return state.markFailed(this);
    }

    public synchronized boolean refund() {
        return state.refund(this);
    }

    public CompletableFuture<OrderStatus> getFuture() {
        return future;
    }
}