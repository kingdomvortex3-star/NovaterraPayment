
package id.novaterra.payment.economy;

public final class CoinValidator {

    private static final long MAX_BALANCE =
            9_000_000_000_000_000L;

    private static final long MAX_TRANSACTION =
            1_000_000_000_000L;

    private CoinValidator() {
    }

    public static boolean isValidAmount(long amount) {
        return amount > 0
                && amount <= MAX_TRANSACTION;
    }

    public static boolean isValidBalance(long balance) {
        return balance >= 0
                && balance <= MAX_BALANCE;
    }

    public static boolean canAdd(
            long currentBalance,
            long amount
    ) {
        if (!isValidBalance(currentBalance)
                || !isValidAmount(amount)) {
            return false;
        }

        return amount <= MAX_BALANCE - currentBalance;
    }

    public static boolean canRemove(
            long currentBalance,
            long amount
    ) {
        if (!isValidBalance(currentBalance)
                || !isValidAmount(amount)) {
            return false;
        }

        return currentBalance >= amount;
    }

    public static long calculateNewBalance(
            long currentBalance,
            long amount
    ) {
        if (amount > 0) {
            if (!canAdd(currentBalance, amount)) {
                throw new IllegalArgumentException(
                        "Invalid coin addition."
                );
            }

            return currentBalance + amount;
        }

        if (amount < 0) {
            if (amount == Long.MIN_VALUE
                    || !canRemove(currentBalance, -amount)) {
                throw new IllegalArgumentException(
                        "Invalid coin deduction."
                );
            }

            return currentBalance + amount;
        }

        return currentBalance;
    }
}
