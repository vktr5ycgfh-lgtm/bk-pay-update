# Scope derived from the supplied Riders Pay presentation

The provided 100-second presentation is a **product overview**, not a complete Android UI recording. UI screens in this implementation are original native layouts based on the presentation's black/yellow branding and stated workflow. The presentation contains concept mockups; it does not supply buildable source, live payment-provider credentials, or certified fare-meter approval.

| Presentation section | v0.3 beta treatment |
|---|---|
| Driver sets an independent manual rate card and switches Duty ON | Driver onboarding + editable rate card + Duty ON/OFF |
| Driver initiates roadside ride via app or floating controls | Main-app start plus a draggable floating HUD; long-press reveals six edge-aware ride actions over map/navigation apps |
| GPS tracks trip distance and computes configured fare | Foreground location service + fare engine + live distance, time and estimate |
| On completion, dynamic UPI QR is generated, ride and payment recorded | End trip saves local record + payee-specific, amount-filled UPI QR + optional manual payment status |
| Verified payment confirmation where provider integration supports it | NOT IMPLEMENTED; driver checks payment independently |
| 3% proposed platform commission and driver cap/incentive concepts | Future/back-end phase; cannot claim automatic commission collection or settlement without payment-provider approval |
| NEED RIDE customer app and fair queue | Future separate customer/dispatch project |
| Proposed Smart Meter/POS hardware | Future device R&D; presentation explicitly says not manufactured |

## Screens in this yellow UI beta

Logo-branded yellow Splash → Three-step Driver Setup → Driver Home with Duty controls → Floating HUD over maps → Long-press radial ride actions → Live Ride Meter → End Ride → Dedicated Fare QR screen + manual payment record → Visual Trip Ledger → Settings.

## Payment safety

UPI QR displays an amount and payee chosen by the driver. A QR scan itself does not mean payment succeeded. This app has **no payment callback, no automatic verification, no payment-split or commission logic, and no bank integration**. All payment markers in the ledger are manual.

## Testing gates

1. Install debug APK built via the bundled GitHub Actions workflow or Android Studio.
2. Enter a rate card valid for your operating jurisdiction.
3. Verify duty state, GPS permissions, trip duration, GPS readings, and return from a map app.
4. Allow “display over other apps”, drag the HUD to both screen edges, long-press it, and verify the arc opens inward and all six actions respond.
5. Scan a test UPI QR and independently check that payee/amount are correct. Do not assume banking settlement.
6. Restart, test local History, and test handling of denied location/overlay permissions and no GPS signal.
7. Obtain any locally required regulatory approvals before treating GPS-calculated amounts as an official auto fare meter.
