package com.flashsale.observer;

import com.flashsale.model.Order;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class OrderPublisher {
    private final List<Notifier> observers;

    public OrderPublisher() {
        this.observers = new CopyOnWriteArrayList<>();
    }

    public void addObserver(Notifier observer) {
        observers.add(observer);
    }

    public void removeObserver(Notifier observer) {
        observers.remove(observer);
    }

    public void notifyObservers(Order order) {
        for (Notifier observer : observers) {
            observer.update(order);
        }
    }
}