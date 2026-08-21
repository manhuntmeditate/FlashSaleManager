import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InventoryManager {
    private final ConcurrentHashMap<Integer, AtomicInteger> inventory;

    public InventoryManager() {
        this.inventory = new ConcurrentHashMap<>();
    }

    public void addProduct(int productId, int quantity) {
        inventory.computeIfAbsent(productId, k -> new AtomicInteger(0))
                 .addAndGet(quantity);
    }

    public boolean removeProduct(int productId, int quantity) {
        AtomicInteger stock = inventory.get(productId);
        if (stock == null) {
            return false;
        }

        while (true) {
            int current = stock.get();
            if (current < quantity) {
                return false; // Out of stock / insufficient quantity
            }
            // Atomic CAS operation
            if (stock.compareAndSet(current, current - quantity)) {
                return true;
            }
        }
    }

    public int getAvailableQuantity(int productId) {
        AtomicInteger stock = inventory.get(productId);
        return stock == null ? 0 : stock.get();
    }
}