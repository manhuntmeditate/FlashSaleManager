package com.flashsale.strategy;

import com.flashsale.model.Product;

public class FlatDiscountStrategy implements PricingStrategy {
    private final int flatDiscountPerUnit;

    public FlatDiscountStrategy(int flatDiscountPerUnit) {
        this.flatDiscountPerUnit = flatDiscountPerUnit;
    }

    @Override
    public int calculatePrice(Product product, int quantity) {
        int unitPrice = Math.max(0, product.getPrice() - flatDiscountPerUnit);
        return unitPrice * quantity;
    }
}