package com.flashsale.observer;

import com.flashsale.model.Order;

public class EmailNotifier implements Notifier {

    @Override
    public void update(Order order) {
        sendEmail(order);
    }

    private void sendEmail(Order order) {
        // Simulate sending an email
        System.out.println("[EmailService] Order ID: " + order.getOrderId() 
                + " | User ID: " + order.getUserId() 
                + " | Status: " + order.getStatus()
                + " | Sale ID: " + order.getSaleId());
    }
}