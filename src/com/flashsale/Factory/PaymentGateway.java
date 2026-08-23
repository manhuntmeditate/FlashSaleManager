package com.flashsale.Factory;

public interface PaymentGateway {
    void recordSuccess();
    void recordFailure();
    int getSuccessCount();
    int getFailureCount();
}