package com.flashsale.service;

import com.flashsale.Factory.PaymentGateway;
import com.flashsale.model.Order;

public class PaymentProcessor {

    public boolean processPayment(Order order) {
        // Simulated network delay: 50ms - 100ms
        try {
            long delay = 50 + (long) (Math.random() * 50);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Record failure on interrupted thread
            if (order.getPaymentMethod() != null) {
                order.getPaymentMethod().getGateway().recordFailure();
            }
            return false;
        }

        // 90% chance of success (true), 10% chance of failure (false)
        boolean success = Math.random() < 0.90;

        // Retrieve the singleton gateway strategy via the order's PaymentMethodEnum
        PaymentGateway gateway = order.getPaymentMethod().getGateway();
        // mock pay method processing and record the result
        if (success) {
            gateway.recordSuccess();
        } else {
            gateway.recordFailure();
        }

        return success;
    }
}