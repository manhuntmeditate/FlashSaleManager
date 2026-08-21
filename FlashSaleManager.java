import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class FlashSaleManager {
    private static volatile FlashSaleManager instance;

    private final InventoryManager inventoryManager;
    private final PaymentProcessor paymentProcessor;
    private final FlashSale flashSale;
    private final AtomicInteger orderIdCounter = new AtomicInteger(1);
    private final long startTimeStamp;

    private final BlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
    private final Map<Integer, Order> orders = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> userPurchases = new ConcurrentHashMap<>();

    private FlashSaleManager(InventoryManager inventoryManager, PaymentProcessor paymentProcessor, FlashSale flashSale) {
        this.inventoryManager = inventoryManager;
        this.paymentProcessor = paymentProcessor;
        this.flashSale = flashSale;
        this.startTimeStamp = System.currentTimeMillis();
    }

    public static FlashSaleManager getInstance(InventoryManager inv, PaymentProcessor pay, FlashSale flashSale) {
        if (instance == null) {
            synchronized (FlashSaleManager.class) {
                if (instance == null) {
                    instance = new FlashSaleManager(inv, pay, flashSale);
                }
            }
        }
        return instance;
    }

    // ==========================================
    // ORCHESTRATION METHODS
    // ==========================================

    public Order checkoutItem(int userId, Product product, int quantity) {
        // 1. Validate sale active
        if (!isSaleActive()) {
            return null;
        }

        // 2. Validate quota
        if (!isQuotaAvailable(userId, quantity)) {
            return null;
        }

        // 3. Reserve inventory
        if (!inventoryManager.removeProduct(product.getId(), quantity)) {
            return null;
        }

        // 4. Record quota reservation
        recordUserQuota(userId, quantity);

        // 5. Create, store, and enqueue order
        Order order = createAndStoreOrder(userId, product, quantity);
        orderQueue.offer(order);

        return order;
    }

    public boolean processNextOrder() throws InterruptedException {
        Order order = orderQueue.take();

        boolean paid = paymentProcessor.processPayment(order.getOrderId(), order.getAmount());

        if (paid) {
            order.setStatus("SUCCESS");
        } else {
            handleOrderFailure(order);
        }

        return true;
    }

    // ==========================================
    // DELEGATED CALCULATION & HELPER METHODS
    // ==========================================

    public boolean isSaleActive() {
        return flashSale.isLive(getElapsedTime());
    }

    public boolean isQuotaAvailable(int userId, int quantity) {
        int currentPurchased = getUserPurchasedQuantity(userId);
        return (currentPurchased + quantity) <= flashSale.getMaxLimitPerUser();
    }

    public int getUserPurchasedQuantity(int userId) {
        return userPurchases.getOrDefault(userId, 0);
    }

    private void recordUserQuota(int userId, int quantity) {
        userPurchases.put(userId, getUserPurchasedQuantity(userId) + quantity);
    }

    private void rollbackUserQuota(int userId, int quantity) {
        int currentPurchased = getUserPurchasedQuantity(userId);
        userPurchases.put(userId, Math.max(0, currentPurchased - quantity));
    }

    private Order createAndStoreOrder(int userId, Product product, int quantity) {
        int totalAmount = flashSale.getPricingStrategy().calculatePrice(product, quantity);
        int orderId = orderIdCounter.getAndIncrement();

        Order order = new Order(orderId, userId, product.getId(), quantity, totalAmount);
        orders.put(orderId, order);
        return order;
    }

    private void handleOrderFailure(Order order) {
        inventoryManager.addProduct(order.getProductId(), order.getQuantity());
        rollbackUserQuota(order.getUserId(), order.getQuantity());
        order.setStatus("FAILED");
    }

    // ==========================================
    // STATUS & METRICS
    // ==========================================

    public String getOrderStatus(int orderId) {
        Order order = orders.get(orderId);
        return (order == null) ? "NOT_FOUND" : order.getStatus();
    }

    public BlockingQueue<Order> getOrderQueue() {
        return orderQueue;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTimeStamp;
    }
}
