package com.flashsale.State;
import com.flashsale.model.Order;
import com.flashsale.model.OrderStatus;
public interface OrderState {
    boolean markSuccess(Order order);
    boolean markFailed(Order order);
    boolean refund(Order order);
    OrderStatus getStatusName();
}