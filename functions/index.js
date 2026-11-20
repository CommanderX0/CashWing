const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const crypto = require('crypto');

initializeApp();
const db = getFirestore();

function requireAuth(request) {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Sign in required');
  const phone = request.auth.token.phone_number;
  if (typeof phone !== 'string' || !/^\+20\d{10}$/.test(phone)) {
    throw new HttpsError('failed-precondition', 'A verified Egyptian phone number is required');
  }
  return { uid: request.auth.uid, phone };
}

// Creates the profile and demo wallet exactly once after phone verification.
exports.initializeAccount = onCall({ enforceAppCheck: false }, async (request) => {
  const { uid, phone } = requireAuth(request);
  const name = String((request.data || {}).displayName || '').trim();
  if (name.length < 2 || name.length > 40) {
    throw new HttpsError('invalid-argument', 'Name must be 2 to 40 characters');
  }
  const userRef = db.collection('users').doc(uid);
  const walletRef = db.collection('wallets').doc(uid);
  await db.runTransaction(async (tx) => {
    const [userSnap, walletSnap] = await Promise.all([tx.get(userRef), tx.get(walletRef)]);
    if (!userSnap.exists) {
      tx.create(userRef, {
        displayName: name,
        phoneE164: phone,
        createdAt: FieldValue.serverTimestamp()
      });
    } else if (userSnap.get('phoneE164') !== phone) {
      throw new HttpsError('permission-denied', 'Phone number does not match this account');
    }
    if (!walletSnap.exists) {
      // Demo-only starter credit (20,000 EGP). Never use this minting behavior for real money.
      tx.create(walletRef, { balancePiasters: 2000000, currency: 'EGP', demoCredit: true, updatedAt: FieldValue.serverTimestamp() });
    }
  });
  return { ok: true };
});

// Recipient lookup stays server-side because Firestore rules do not expose other users' profiles.
exports.lookupRecipient = onCall({ enforceAppCheck: false }, async (request) => {
  requireAuth(request);
  const phone = (request.data || {}).phoneE164;
  if (typeof phone !== 'string' || !/^\+20\d{10}$/.test(phone)) {
    throw new HttpsError('invalid-argument', 'Invalid recipient phone');
  }
  const snap = await db.collection('users').where('phoneE164', '==', phone).limit(2).get();
  if (snap.size !== 1) throw new HttpsError('not-found', 'Recipient account not found');
  return { displayName: snap.docs[0].get('displayName') || 'CashWing user' };
});

// All money movement is performed server-side in integer piastres.
exports.transferFunds = onCall({ enforceAppCheck: false }, async (request) => {
  const { uid: senderUid } = requireAuth(request);
  const { recipientPhoneE164, amountPiasters, requestId } = request.data || {};
  if (typeof recipientPhoneE164 !== 'string' || !/^\+20\d{10}$/.test(recipientPhoneE164)) {
    throw new HttpsError('invalid-argument', 'Invalid recipient phone');
  }
  if (!Number.isSafeInteger(amountPiasters) || amountPiasters <= 0 || amountPiasters > 100000000) {
    throw new HttpsError('invalid-argument', 'Invalid amount');
  }
  if (typeof requestId !== 'string' || requestId.length < 12 || requestId.length > 100) {
    throw new HttpsError('invalid-argument', 'Invalid request id');
  }

  const recipientSnap = await db.collection('users').where('phoneE164', '==', recipientPhoneE164).limit(2).get();
  if (recipientSnap.size !== 1) throw new HttpsError('not-found', 'Recipient account not found');
  const recipientUid = recipientSnap.docs[0].id;
  if (recipientUid === senderUid) throw new HttpsError('invalid-argument', 'Cannot transfer to yourself');

  const senderRef = db.collection('wallets').doc(senderUid);
  const recipientRef = db.collection('wallets').doc(recipientUid);
  const txId = crypto.createHash('sha256').update(`${senderUid}:${requestId}`).digest('hex');
  const transferRef = db.collection('transactions').doc(txId);

  await db.runTransaction(async (tx) => {
    const [prior, sender, recipient] = await Promise.all([
      tx.get(transferRef), tx.get(senderRef), tx.get(recipientRef)
    ]);
    if (prior.exists) {
      const old = prior.data();
      if (old.senderUid !== senderUid || old.receiverUid !== recipientUid || old.amountPiasters !== amountPiasters) {
        throw new HttpsError('already-exists', 'Request id was already used for another transfer');
      }
      return;
    }
    if (!sender.exists || !recipient.exists) throw new HttpsError('failed-precondition', 'Wallet not initialized');
    const senderBalance = sender.get('balancePiasters');
    const receiverBalance = recipient.get('balancePiasters');
    if (!Number.isSafeInteger(senderBalance) || !Number.isSafeInteger(receiverBalance)) {
      throw new HttpsError('failed-precondition', 'Wallet balance is invalid');
    }
    if (senderBalance < amountPiasters) throw new HttpsError('failed-precondition', 'Insufficient balance');
    if (!Number.isSafeInteger(receiverBalance + amountPiasters)) throw new HttpsError('out-of-range', 'Recipient balance limit exceeded');

    tx.update(senderRef, { balancePiasters: senderBalance - amountPiasters, updatedAt: FieldValue.serverTimestamp() });
    tx.update(recipientRef, { balancePiasters: receiverBalance + amountPiasters, updatedAt: FieldValue.serverTimestamp() });
    tx.create(transferRef, {
      senderUid, receiverUid: recipientUid, amountPiasters,
      status: 'completed', createdAt: FieldValue.serverTimestamp(), requestId, currency: 'EGP'
    });
  });
  return { ok: true };
});
