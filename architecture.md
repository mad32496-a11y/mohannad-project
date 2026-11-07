# Architecture: Mohannad (name chosen by the owner)

Input: replica/recon.md and replica/features.csv. Method: replica-architect, written from the README's description ("plans the stack, database schema and API"), not from the original SKILL.md.

## 1. Key constraint: this must be a native Android app
The core feature (read another app's offer screen and tap "accept") needs Android's AccessibilityService. iOS has no equivalent, and a web app or Next.js/Postgres stack cannot do it. So the README's example stack (Next.js) does not apply here.

## 2. Recommended stack
| Layer | Choice | Why |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose | Native access to AccessibilityService and overlays; Compose gives RTL and the card/stepper/switch components cheaply. |
| Settings storage | Jetpack DataStore | Ten small key-value settings (F006-F011, F014). |
| Trip history database | Room (SQLite) | Local list with price/destination; recon says processing is local. |
| Core engine | Pure Kotlin `DecisionEngine` | Offer + settings in, accept/ignore out. Easy to unit-test with no device. |
| Screen reading / tapping | `AccessibilityService` | The only supported route on Android. |
| Floating bubble | Foreground service + `SYSTEM_ALERT_WINDOW` overlay | F014. |
| Subscriptions and trial | Google Play Billing (subscription with free-trial offer) | Play enforces the trial and renewals, so no server needed in v1. |
| Backend | None in v1 | Add Supabase (Postgres + Auth) only if you later need accounts, cross-device sync or trial-abuse checks. |
| Testing | JUnit (engine), Compose UI tests, an in-app "offer simulator" screen | Lets you test the accept logic without any real ride app. |

## 3. Components
1. **UI (Compose):** Home, Conditions, Trip History, Trial/Subscription, Menu.
2. **SettingsRepository** (DataStore) feeding both UI and engine.
3. **OfferParser (interface), one adapter per supported ride app:** turns an accessibility node tree into `Offer(price?, pickupMinutes?, destination?, hasDestination)`. Adapters are the only part tied to third-party UI. Write them only from your own account and public info.
4. **DecisionEngine:** applies the rules in section 5.
5. **AcceptService (AccessibilityService):** receives events, calls parser, then engine, then performs the click, then writes a TripLog row.
6. **BubbleService:** overlay showing on/off state.
7. **BillingManager:** entitlement check; the engine refuses to accept when there is no active trial or subscription.

## 4. Local data schema (Room / DataStore)
**DataStore `settings`:** auto_accept_enabled, min_price_enabled, min_price (2.0), max_pickup_enabled, max_pickup_minutes (5), price_override_time_enabled, price_override_time_value (0.0), short_trip_override_enabled, accept_no_destination_enabled, bubble_visible.
**Room `trip_log`:** id (PK), accepted_at (epoch ms), price (REAL, nullable), destination (TEXT, nullable), pickup_minutes (INT, nullable), rule_matched (TEXT), source_package (TEXT). Index on accepted_at DESC. Add a retention setting (for example 90 days) and a "clear history" action.
**API:** none in v1. If a backend is added later: `POST /entitlement/verify` (Play purchase token) and optional `GET/PUT /settings` for sync.

## 5. Decision rules (proposed, from the screens' helper texts)
1. Auto-accept off or no valid trial/subscription: ignore.
2. Offer without price/destination: accept only if "accept without destination" is on and the time rule passes.
3. If "price overrides time" is on and price >= override value: skip the time check (the min-price rule still applies).
4. Otherwise: min-price rule (if on) AND max-pickup rule (if on) must both pass.
5. Short-trip override (if on): accept offers whose pickup time is 1-2 minutes even if the min-price rule fails (user-confirmed). Keep it in one function so it is cheap to change.

## 6. Build order
1. DecisionEngine + unit tests (covers F006-F011).
2. Settings UI with DataStore (F001-F003, F006-F013, F020, F021).
3. Offer simulator screen + AcceptService, so the click path works end to end.
4. Trip log + history screen (F015).
5. Bubble overlay (F014).
6. Billing + trial (F016, F017).
7. First real OfferParser adapter, from your own account only.
8. Rebrand sweep, Play policy declaration for Accessibility, store listing.

## 7. Risks to settle before building
- **Third-party terms:** auto-accepting offers in other apps may break their terms and get driver accounts suspended. Read them first.
- **Google Play policy:** AccessibilityService apps need a declaration and a clear in-app disclosure; a policy rejection is a real risk.
- **Fragility:** any change to a ride app's UI can break its parser adapter. Plan for updates.
- **Privacy:** keep screen content on-device only, and make the Data safety form match what the app actually does.
- Not legal advice; check with a lawyer before selling.

## 8. Billing and trial (REMOVED, see section 11)
- **Two layers.** (1) An app-level free trial counted from first launch (`EntitlementRules.TRIAL_DAYS`, 3 days is an assumption), then (2) a Google Play subscription. This matches the original's visible countdown, which runs without any payment.
- **One seam to Play:** `BillingGateway`. `PlayBillingGateway` (billing-ktx 7.x) is the only class that touches the Play API; `FakeBillingGateway` is used in debuggable builds.
- **Pure rules:** `EntitlementRules.resolve(...)` decides Trial / Subscribed / Expired. It includes a clock-rollback guard and a 7-day offline grace for subscribers. All of it is JVM-testable.
- **Enforcement:** `AcceptService` asks `EntitlementManager.state` (re-checked every 30 s, fails closed). The engine already returns `NOT_ENTITLED` when it is false.
- **Known weaknesses (v1):** (a) a local trial can be reset by clearing app data (Auto Backup helps only partly); (b) purchases are trusted on the device, so a rooted device could fake them. The fix for both is a small server: verify purchase tokens with the Play Developer API and bind the trial to the account/device server-side.
- **Play Console to do:** create the subscription product (ID must equal `PlayBillingGateway.PRODUCT_ID`), a base plan, and a licence-tester account; upload to an internal test track (a debug-signed APK cannot query real products).

## 9. Writing the real OfferParsers (Petra, Jeeny): how the data is collected
- **Targets:** the driver apps, not the rider apps. The only Android id found for Petra, `com.PetraRide_User`, is the rider app; the driver app ("Petra Safeer") id was not found, and Jeeny's was not looked up. So the dev tool lists installed apps whose name or package matches and lets the user pick, instead of hard-coding ids.
- **Dev capture tool (debug builds):** `ScreenCapturer` saves accessibility trees of the picked apps while armed (max 10 min or 30 shots, de-duplicated by layout, last 5 slots reserved for screens that look like offers). Package filter is widened at runtime to exactly those apps, then restored.
- **Privacy:** letters are masked by default (digits, button labels and short unit tokens kept); password fields are never saved, even in raw mode; raw mode is opt-in.
- **Output:** `<external files>/dumps/<package>_<time>_<n>[_offer].json` and a zip for sharing.
- **Next:** write `PetraOfferParser` and `JeenyOfferParser` from the dumps, with each dump (redacted) as a JVM test fixture; then add the confirmed package ids to `accessibility_service_config.xml`.

## 10. Jeeny and Petra parsers (written from screenshots, unverified)
**Source:** two screenshots per app (one offer, one idle) from the user's own driver apps. Placeholder text is used in tests; real addresses are not stored.
**What the screenshots showed**
- *Jeeny offer:* type chip ("إيكونومي"), "يبعد 1 دقائق" (pickup minutes), a second distance line that was not legible, fare "JOD 1.50", two unlabelled addresses, purple full-width "قبول العرض" button with a lighter segment on one side (probably a countdown), close "X". *Idle:* "أنت متاح الآن", earnings "JOD 2.31", "إيقاف".
- *Petra offer:* type chip ("تاكسي"), fare "1.44 د.أ", "2.88 دقيقة • 830 متر" (decimal minutes), labelled "نقطة الانطلاق" and "نقطة الوصول", orange "قبول", small red "رفض" top-left, a progress bar (countdown). *Idle:* "أنا متاح / قيد البحث عن الطلبات", balance "8.42 د.أ", "انتقل إلى وضع عدم الإتاحة".
**Design choices that come from this**
- Both idle screens show a money amount, so an amount alone never makes an offer: the parser also needs an accept-like label inside a clickable node.
- Jeeny prints "JOD 1.50" (code first) and Petra "1.44 د.أ" (unit after): `OfferText.price` handles both, and Arabic digits.
- Pickup time can be decimal ("2.88"). It is rounded UP, so `ceil(x) <= N` is exactly `x <= N` for the whole-number rules, and 2.88 min does not trigger the 1-2 minute rule.
- Petra's reject button is never a candidate: only the exact label "قبول" inside a clickable node counts.
- Labels are read from `text`, then `contentDescription`, because some UI toolkits fill only the second.
**Still unknown (needs a real capture)**
- Package ids of both driver apps (the user switches the app on in the supported-apps list, so none is hard-coded).
- Whether either app exposes these nodes to accessibility at all, and whether the button answers ACTION_CLICK.
- Whether Jeeny's accept is a plain tap (a drained button could also be a hold-to-accept control).
- How long an offer stays up, and what the second Jeeny distance line says.

## 11. Owner decisions after section 10
- **No trial, no subscription.** Access is always on: `AcceptService` passes `entitled = { true }` to the pipeline. The billing package, the trial store, the subscription screen, the Play Billing dependency and their tests were deleted, which also removes the code that had never been compiled against the Play API. (Section 8 is kept only as history.)
- **Per-app monitoring.** Home has one card per ride app (Jeeny, Petra) with its own switch and status light. A switch writes the chosen installed package into `SupportedAppsStore`; `PackageFilter` then lets the service see only those packages. If several installed apps match one name (for example the rider and driver apps), the user is asked which one. Status comes from `AppStatus.of(...)` (tested).
- **App icon:** adaptive icon (`mipmap-anydpi`), themed monochrome layer for Android 13+, window theme `Theme.Mohannad`.
