public class EmailNotifier implements Notifier {

    @Override
    public void update(Order order) {
        sendEmail(order);
    }

    private void sendEmail(Order order) {
        System.out.println("[EmailService] Order ID: " + order.getOrderId() 
                + " | User ID: " + order.getUserId() 
                + " | Status: " + order.getStatus());
    }
}