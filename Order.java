import java.util.concurrent.CompletableFuture;

public class Order {
    private final int orderId;
    private final int userId;
    private final int productId;
    private final int quantity;
    private final int amount;
    private volatile String status;
    
    // Push notification future for real-time buyer resolution
    private final CompletableFuture<String> future = new CompletableFuture<>();

    public Order(int orderId, int userId, int productId, int quantity, int amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = "PENDING";
    }

    public int getOrderId() { return orderId; }
    public int getUserId() { return userId; }
    public int getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public int getAmount() { return amount; }
    public String getStatus() { return status; }

    public void setStatus(String status) {
        this.status = status;
        // Signal the waiting buyer thread immediately
        this.future.complete(status);
    }

    public CompletableFuture<String> getFuture() {
        return future;
    }
}
