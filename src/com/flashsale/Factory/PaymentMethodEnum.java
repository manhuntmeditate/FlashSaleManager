package com.flashsale.Factory;

public enum PaymentMethodEnum {
    CREDIT_CARD,
    UPI;

    public PaymentGateway getGateway() {
        switch (this) {
            case CREDIT_CARD:
                return CreditCardPaymentGateway.getInstance();
            case UPI:
                return UpiPaymentGateway.getInstance();
            default:
                throw new IllegalArgumentException("Unsupported payment method: " + this);
        }
    }
}