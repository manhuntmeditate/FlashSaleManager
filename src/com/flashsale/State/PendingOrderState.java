package com.flashsale.State;
import com.flashsale.model.Order;

public class PendingOrderState implements OrderState {
    @Override
    public boolean markSuccess(Order order) {
        order.setState(new SuccessOrderState());
        return true;
    }
    @Override
    public boolean markFailed(Order order) {
        order.setState(new FailedOrderState());
        return true;
    }
    @Override
    public boolean refund(Order order) {
        return false;
    }
    @Override
    public String getStatusName() {
        return "PENDING";
    }     
}
