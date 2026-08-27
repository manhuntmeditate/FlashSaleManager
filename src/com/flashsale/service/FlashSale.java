package com.flashsale.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.flashsale.model.Product;
import com.flashsale.strategy.PricingStrategy;

public class FlashSale {
    private final int saleId;
    private final PricingStrategy pricingStrategy;
    private final long startTime;
    private final long endTime;
    private final int maxLimitPerUser;
    private final InventoryManager inventoryManager;
    private final Map<Integer, Integer> userPurchases = new ConcurrentHashMap<>();

    public FlashSale(int saleId, PricingStrategy pricingStrategy, long startTime, long endTime, int maxLimitPerUser, InventoryManager inventoryManager) {
        this.saleId = saleId;
        this.pricingStrategy = pricingStrategy;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxLimitPerUser = maxLimitPerUser;
        this.inventoryManager = inventoryManager;
    }

    public int getSaleId() { return saleId; }
    public PricingStrategy getPricingStrategy() { return pricingStrategy; }
    public int getMaxLimitPerUser() { return maxLimitPerUser; }
    public InventoryManager getInventoryManager() { return inventoryManager; }

    public boolean isLive(long currentElapsedTime) {
        return currentElapsedTime >= startTime && currentElapsedTime <= endTime;
    }

    public int getUserPurchasedQuantity(int userId) {
        return userPurchases.getOrDefault(userId, 0);
    }

    public boolean isQuotaAvailable(int userId, int quantity) {
        return (getUserPurchasedQuantity(userId) + quantity) <= maxLimitPerUser;
    }

    public boolean tryReserveQuotaAndStock(int userId, Product product, int quantity, long currentElapsedTime) {
        if (!isLive(currentElapsedTime)) {
            return false;
        }

        if (!isQuotaAvailable(userId, quantity)) {
            return false;
        }

        if (!inventoryManager.removeProduct(product.getId(), quantity)) {
            return false;
        }

        // Record purchase quota non-blockingly
        userPurchases.put(userId, getUserPurchasedQuantity(userId) + quantity);
        return true;
    }

    public void releaseQuotaAndStock(int userId, int productId, int quantity) {
        inventoryManager.addProduct(productId, quantity);
        int currentPurchased = getUserPurchasedQuantity(userId);
        userPurchases.put(userId, Math.max(0, currentPurchased - quantity));
    }

    public int calculatePrice(Product product, int quantity) {
        return pricingStrategy.calculatePrice(product, quantity);
    }
}
