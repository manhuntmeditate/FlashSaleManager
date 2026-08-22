package com.flashsale.strategy;

import com.flashsale.model.Product;

public class PercentageDiscountStrategy implements PricingStrategy {
    private final int discountPercent;

    public PercentageDiscountStrategy(int discountPercent) {
        this.discountPercent = discountPercent;
    }

    @Override
    public int calculatePrice(Product product, int quantity) {
        int unitPrice = (int) (product.getPrice() * (1.0 - (discountPercent / 100.0)));
        return unitPrice * quantity;
    }
}