package com.example.quickpay;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

/** User-owned profile data. Wallet balance is deliberately not writable from Android clients. */
public final class CloudProfileRepository {
    public interface Result { void onSuccess(); void onError(String message); }
    private final FirebaseFirestore store = FirebaseFirestore.getInstance();
    private DocumentReference profile() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) throw new IllegalStateException("Sign in first");
        return store.collection("users").document(FirebaseAuth.getInstance().getCurrentUser().getUid());
    }
    public void createProfile(String displayName, Result result) {
        Map<String,Object> data = new HashMap<>();
        data.put("displayName", displayName); data.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        String phone = PhoneCredential.phoneForEmail(FirebaseAuth.getInstance().getCurrentUser().getEmail());
        data.put("phoneE164", phone);
        data.put("phoneKey", PhoneCredential.phoneKey(phone));
        data.put("loginEmail", FirebaseAuth.getInstance().getCurrentUser().getEmail());
        profile().set(data).addOnSuccessListener(v -> result.onSuccess()).addOnFailureListener(e -> result.onError(e.getMessage()));
    }
    public void observeProfile(com.google.firebase.firestore.EventListener<com.google.firebase.firestore.DocumentSnapshot> listener) { profile().addSnapshotListener(listener); }
}
