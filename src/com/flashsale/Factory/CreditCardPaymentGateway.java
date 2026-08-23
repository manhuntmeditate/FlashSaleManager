package com.flashsale.Factory;

import java.util.concurrent.atomic.AtomicInteger;

public class CreditCardPaymentGateway implements PaymentGateway {
    // 1. volatile keyword prevents instruction reordering issues across threads
    private static volatile CreditCardPaymentGateway instance;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    // 2. Private constructor prevents direct instantiation
    private CreditCardPaymentGateway() {}

    public static CreditCardPaymentGateway getInstance() {
        if (instance == null) { 
            synchronized (CreditCardPaymentGateway.class) {
                if (instance == null) {
                    instance = new CreditCardPaymentGateway();
                }
            }
        }
        return instance;
    }

    @Override
    public void recordSuccess() {
        successCount.incrementAndGet();
    }

    @Override
    public void recordFailure() {
        failureCount.incrementAndGet();
    }

    @Override
    public int getSuccessCount() {
        return successCount.get();
    }

    @Override
    public int getFailureCount() {
        return failureCount.get();
    }
}