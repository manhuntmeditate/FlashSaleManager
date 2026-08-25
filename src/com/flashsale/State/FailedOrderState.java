package com.flashsale.State;
import com.flashsale.model.Order;
import com.flashsale.model.OrderStatus;
public class FailedOrderState implements OrderState {
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
        return OrderStatus.FAILED;
    }
}
