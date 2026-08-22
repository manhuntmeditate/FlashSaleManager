package com.flashsale.service;

import com.flashsale.strategy.PricingStrategy;

public class FlashSale {
    private final PricingStrategy pricingStrategy;
    private final long startTime;
    private final long endTime;
    private final int maxLimitPerUser;

    public FlashSale(PricingStrategy pricingStrategy, long startTime, long endTime, int maxLimitPerUser) {
        this.pricingStrategy = pricingStrategy;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxLimitPerUser = maxLimitPerUser;
    }

    public PricingStrategy getPricingStrategy() { return pricingStrategy; }
    public int getMaxLimitPerUser() { return maxLimitPerUser; }

    public boolean isLive(long currentElapsedTime) {
        return currentElapsedTime >= startTime && currentElapsedTime <= endTime;
    }
}