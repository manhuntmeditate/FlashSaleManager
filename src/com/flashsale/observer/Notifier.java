package com.flashsale.observer;
import com.flashsale.model.Order;

public interface Notifier {
    void update(Order order);
}