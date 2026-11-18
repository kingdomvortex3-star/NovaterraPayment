
package id.novaterra.payment.transaction;

public enum TransactionStatus {

    PENDING,
    WAITING_PAYMENT,
    PAID,
    PROCESSING,
    COMPLETED,
    FAILED,
    EXPIRED,
    CANCELLED,
    REFUNDED;

    public boolean isFinal() {
        return this == COMPLETED
                || this == FAILED
                || this == EXPIRED
                || this == CANCELLED
                || this == REFUNDED;
    }

    public boolean canDeliverCoins() {
        return this == PAID
                || this == PROCESSING;
    }

    public boolean isPaid() {
        return this == PAID
                || this == PROCESSING
                || this == COMPLETED;
    }
}
