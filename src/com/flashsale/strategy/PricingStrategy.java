package com.flashsale.strategy;

import com.flashsale.model.Product;

public interface PricingStrategy {
    int calculatePrice(Product product, int quantity);
}