# CashWing (Demo Wallet)

CashWing is an Android demo wallet built with Java, Android Studio, Firebase Authentication, and Cloud Firestore.

## Included features
- Phone-number + 6-digit PIN sign-in using Firebase Email/Password credentials (not SMS phone verification).
- Demo EGP wallet and simulated transfers between registered accounts.
- Recent activity, searchable transaction history, transaction detail dialogs, and monthly reports.
- Profile display-name editing and PIN change.
- Account QR generation and QR scanning to prefill a demo transfer.
- Light/dark appearance preference.
- Incoming-transfer notifications while the app is running in the foreground.

## Important limitations
- All balances and transfers are simulated. This project must not be used to store or move real money.
- The PIN-based sign-in is a demo workaround and does not verify ownership of the phone number.
- Incoming notifications in this build are foreground/in-app-device notifications. Push notifications while the app is closed require a trusted server-side sender (for example, a properly secured Firebase Cloud Function or another backend).
- Client-side Firestore wallet updates are not suitable for production financial transactions.

## Firebase setup
Keep the existing `app/google-services.json`, Firebase project, and application ID (`com.example.quickpay`) for this app to use the same Firebase project and existing accounts. Do not replace them with a new Firebase project if you want to keep using the current demo accounts.

Review and deploy Firestore rules before distributing the app. Do not publish private credentials or production secrets.
