package com.flashsale.State;
import com.flashsale.model.OrderStatus;
import com.flashsale.model.Order;

public class RefundOrderState implements OrderState{
    @Override
    public boolean markSuccess(Order order) {
        return false;
    }

    @Override
    public boolean markFailed(Order order) {
        return false;
    }

    @Override
    public boolean refund(Order order) {
        return false;
    }
    @Override
    public OrderStatus getStatusName() {
        return OrderStatus.REFUNDED;
    }
}

