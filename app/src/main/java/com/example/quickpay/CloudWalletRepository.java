package com.example.quickpay;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;

/** Demo-only Firestore wallet. Client-side transfers are simulated and are NOT secure for real money. */
public final class CloudWalletRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    public String uid() { return FirebaseAuth.getInstance().getCurrentUser() == null ? null : FirebaseAuth.getInstance().getCurrentUser().getUid(); }
    public Task<DocumentSnapshot> profile() { return db.collection("users").document(uid()).get(); }
    public Task<DocumentSnapshot> userProfile(String userUid) { return db.collection("users").document(userUid).get(); }
    public Task<DocumentSnapshot> wallet() { return db.collection("wallets").document(uid()).get(); }

    public Task<QuerySnapshot> findUser(String e164) {
        return db.collection("users").whereEqualTo("phoneKey", PhoneCredential.phoneKey(e164)).limit(1).get();
    }

    public Task<Void> transfer(String receiverE164, long piasters) {
        String senderUid = uid();
        if (senderUid == null) throw new IllegalStateException("Please sign in again");
        if (piasters <= 0) throw new IllegalArgumentException("Invalid amount");
        return findUser(receiverE164).continueWithTask(findTask -> {
            if (!findTask.isSuccessful()) throw findTask.getException();
            QuerySnapshot found = findTask.getResult();
            if (found == null || found.isEmpty()) throw new IllegalStateException("Receiver account not found");
            DocumentSnapshot recipient = found.getDocuments().get(0);
            String receiverUid = recipient.getId();
            if (senderUid.equals(receiverUid)) throw new IllegalStateException("You cannot send money to yourself");
            return db.collection("users").document(senderUid).get().continueWithTask(senderProfileTask -> {
                if (!senderProfileTask.isSuccessful()) throw senderProfileTask.getException();
                DocumentSnapshot senderProfile = senderProfileTask.getResult();
                String senderName = senderProfile == null ? null : senderProfile.getString("displayName");
                DocumentReference senderWallet = db.collection("wallets").document(senderUid);
                DocumentReference receiverWallet = db.collection("wallets").document(receiverUid);
                DocumentReference tx = db.collection("transactions").document(UUID.randomUUID().toString());
                return db.runTransaction(transaction -> {
                DocumentSnapshot sender = transaction.get(senderWallet);
                DocumentSnapshot receiver = transaction.get(receiverWallet);
                long senderBalance = sender.getLong("balancePiasters") == null ? 0L : sender.getLong("balancePiasters");
                long receiverBalance = receiver.getLong("balancePiasters") == null ? 0L : receiver.getLong("balancePiasters");
                if (senderBalance < piasters) throw new FirebaseFirestoreException("Insufficient balance", FirebaseFirestoreException.Code.ABORTED);
                transaction.update(senderWallet, "balancePiasters", senderBalance - piasters);
                transaction.update(receiverWallet, "balancePiasters", receiverBalance + piasters);
                Map<String,Object> data = new HashMap<>();
                data.put("senderUid", senderUid); data.put("receiverUid", receiverUid);
                data.put("senderPhone", PhoneCredential.phoneForEmail(FirebaseAuth.getInstance().getCurrentUser().getEmail()));
                data.put("receiverPhone", receiverE164);
                String receiverName = recipient.getString("displayName");
                if (receiverName != null && !receiverName.trim().isEmpty()) data.put("receiverName", receiverName.trim());
                if (senderName != null && !senderName.trim().isEmpty()) data.put("senderName", senderName.trim());
                data.put("amountPiasters", piasters);
                data.put("status", "completed"); data.put("demo", true); data.put("createdAt", FieldValue.serverTimestamp());
                transaction.set(tx, data);
                return null;
                });
            });
        });
    }

    // Keep these queries equality-only. Sorting is done locally so the app does
    // not depend on composite Firestore indexes being deployed for these screens.
    // These are demo-app histories; for a production-scale ledger, use paginated
    // server queries and deploy the matching indexes.
    public Task<QuerySnapshot> sentTransactions() {
        String currentUid = uid();
        if (currentUid == null) throw new IllegalStateException("Please sign in again");
        return db.collection("transactions").whereEqualTo("senderUid", currentUid).get();
    }
    public Task<QuerySnapshot> receivedTransactions() {
        String currentUid = uid();
        if (currentUid == null) throw new IllegalStateException("Please sign in again");
        return db.collection("transactions").whereEqualTo("receiverUid", currentUid).get();
    }
    public static double egp(DocumentSnapshot wallet) {
        Long p = wallet == null ? null : wallet.getLong("balancePiasters");
        return p == null ? 0.0 : p / 100.0;
    }
}
