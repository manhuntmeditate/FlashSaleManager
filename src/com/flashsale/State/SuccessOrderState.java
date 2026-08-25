package com.flashsale.State;
import com.flashsale.model.OrderStatus;
import com.flashsale.model.Order;

public class SuccessOrderState implements OrderState{
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
        order.setState(new RefundOrderState());
        return true;
    }
    @Override
    public OrderStatus getStatusName() {
        return OrderStatus.SUCCESS;
    }
}
