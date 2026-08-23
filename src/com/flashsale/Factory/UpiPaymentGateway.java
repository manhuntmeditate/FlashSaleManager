package com.flashsale.Factory;

import java.util.concurrent.atomic.AtomicInteger;

public class UpiPaymentGateway implements PaymentGateway {
    // 1. volatile ensures visibility and prevents half-initialized object publishing
    private static volatile UpiPaymentGateway instance;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    // 2. Private constructor prevents direct instantiation
    private UpiPaymentGateway() {}

    // 3. Double-Checked Locking (DCL)
    public static UpiPaymentGateway getInstance() {
        if (instance == null) { // First check
            synchronized (UpiPaymentGateway.class) {
                if (instance == null) { // Second check
                    instance = new UpiPaymentGateway();
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