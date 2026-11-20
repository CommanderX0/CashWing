# CashWing – phone + PIN login with Firebase Spark

## How account access works
- Users create an account with an Egyptian phone number, name, and 6-digit PIN.
- The app silently maps the normalized phone number to a generated Firebase email identity and uses Firebase Email/Password Authentication. The user continues to enter only phone + PIN.
- The Firebase Auth account is the durable identity. The Firestore profile and demo wallet are stored under that Auth UID, so the same phone + PIN can sign in on another device or after reinstalling the app.
- No SMS, OTP screen, Phone Authentication, Cloud Functions, or Blaze billing is used.
- The phone number is NOT verified as belonging to the user. Anyone who knows or guesses the PIN can attempt to access that account. A six-digit PIN is weak for a financial credential; this project is a demo only.

## Firebase Console setup
1. Keep the Firebase project on the Spark plan.
2. Open Firebase Console → Authentication → Sign-in method and enable **Email/Password**. Do not enable Phone Authentication for this flow.
3. Create Cloud Firestore if it does not already exist.
4. Open Firestore → Rules and publish the exact contents of this project's `firestore.rules` file.
5. Confirm the Android app in Firebase Project settings uses package name `com.example.quickpay` and that `app/google-services.json` belongs to project `cashwing-53832`.
6. Sync Gradle in Android Studio, then run the app with an internet connection.

## Login behavior
- Registration creates the Firebase Authentication account first, then creates `users/{uid}` and `wallets/{uid}` in a Firestore transaction.
- Login calls Firebase Email/Password with the phone-derived identity and PIN, then loads the matching Firestore profile.
- Firebase Authentication stores the account independently of the installed app. Reinstalling the app or using another device does not delete the account; sign in again with the same phone and PIN.
- If the previous version created Phone-Authentication-only test accounts, those accounts are not automatically converted to this login method. Create a new account with this version for the new phone+PIN sign-in flow. Do not delete existing Firestore data unless you have a backup.

## Demo wallet warning
- New demo wallets start with 20,000 EGP (2,000,000 piastres).
- Transfers are simulated only. Client-side Firestore rules intentionally permit demo balance changes and are not secure against a modified app.
- Never use this project or its rules for real funds. A real-money wallet requires a trusted backend, secure identity verification, fraud controls, and a regulated payment integration.
