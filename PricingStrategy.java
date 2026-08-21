public interface PricingStrategy {
    int calculatePrice(Product product, int quantity);
}