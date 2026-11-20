package com.example.quickpay;

/** Compatibility wrapper for the demo-only Firestore transfer flow. */
public final class CloudTransferRepository {
    public interface Callback { void onComplete(boolean ok, String message); }
    public void transfer(String recipientPhoneE164, long amountPiasters, String requestId, Callback callback) {
        new CloudWalletRepository().transfer(recipientPhoneE164, amountPiasters)
                .addOnSuccessListener(v -> callback.onComplete(true, "Demo transfer completed"))
                .addOnFailureListener(e -> callback.onComplete(false, e.getLocalizedMessage()));
    }
}
