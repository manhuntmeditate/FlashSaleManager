package com.flashsale.State;
import com.flashsale.model.Order;
public interface OrderState {
    boolean markSuccess(Order order);
    boolean markFailed(Order order);
    boolean refund(Order order);
    String getStatusName();
}