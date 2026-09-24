# Architecture and operational contract

## State flow

`REQUESTED → ASSIGNED → ARRIVED → ON_TRIP → COMPLETED`

`CANCELLED` may be entered from REQUESTED, ASSIGNED, or ARRIVED. Only Cloud Functions change ride state. The driver and customer clients invoke authenticated callable functions; Firestore rules deny direct ride writes.

## Fair stand queue

Every queue entry has `queueTier` and `effectiveQueueTime`:

- Tier 0: never served. Ordered by the first `joinedStandAt`.
- Tier 1: previously completed or declined. Completed drivers are ordered by `lastTripCompletedAt`.
- A decline/timeout moves that driver behind the current queue and applies `penaltyUntil` for 90 seconds.

Dispatch acquires the ride and queue state inside one Firestore transaction. The ride stays `REQUESTED` during an offer and receives `offeredDriverId`, `offerVersion`, and `offerExpiresAt`. Acceptance is another transaction that verifies driver identity, state, offer version context, and expiry before setting `driverId` and `ASSIGNED`. A versioned Cloud Task runs after 20 seconds; stale timeout tasks cannot affect a later offer.

## Collections

- `drivers/{uid}`: provisioned profile, FCM token, duty/busy flags, stand, geohash, live position/bearing.
- `stands/{standId}/active_queue/{uid}`: private dispatch queue state and copied alert/profile fields.
- `rides/{rideId}`: authoritative request, route quote, assignment and status timestamps.
- `rides/{rideId}/telemetry/current`: current assigned-auto position, bearing and speed.
- `config/fare_rules`: `baseFarePaise`, `baseDistanceMeters`, `perKmPaise`, `minimumFarePaise`.

Example Coimbatore test configuration (illustrative only):

```json
{"baseFarePaise":4000,"baseDistanceMeters":1500,"perKmPaise":1500,"minimumFarePaise":4000}
```

Replace it with the current legally permitted slab before any field trial.

## Cloud deployment

```bash
firebase login
firebase use YOUR_PROJECT_ID
firebase functions:secrets:set GOOGLE_ROUTES_API_KEY
cd functions && npm install && npm run build && cd ..
firebase deploy --only firestore:rules,firestore:indexes,functions
```

Enable Cloud Tasks and grant the Functions runtime service account task-enqueuer permission. Keep the Routes key in Secret Manager; use separate Android-restricted Maps/Places keys in `local.properties`.

## Required production work outside source control

- Provision Firebase Auth users and set immutable `role=driver|customer` custom claims from an admin-only system.
- Provision verified driver profiles and stand memberships; do not let clients self-approve drivers.
- Register both apps with Firebase App Check/Play Integrity and enforce App Check for callable endpoints after test tokens are configured.
- Complete Play Console declarations for foreground location, overlay, notifications and full-screen intents. Android may suppress an FSI if policy/user settings do not allow it; the high-priority notification remains the fallback.
- Configure CI-held release signing, monitoring, backups, retention/deletion, customer support and abuse/rate limits.
- Obtain transport/fare-meter and payment compliance review. UPI QR is a payment request, not settlement confirmation.
