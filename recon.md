# Recon: "أبو كبسة" (Abu Kabsa) — com.abokabsa.farehunter

Method: replica-recon (clean-room). Sources: Google Play listing (public) + 3 screenshots from the user's own install (batch 1).
Legend: **[seen]** = visible in a screenshot · **[listing]** = stated on the Play page · **[inferred]** = my guess, needs confirmation.

## 1. Application Overview
- **Purpose [listing]:** Android driver-assistant. The driver sets conditions (min price, max time); the app watches ride offers on screen and accepts the ones that match.
- **Audience [inferred]:** ride-hailing drivers in Jordan (prices are in dinars; UI is Arabic/RTL).
- **Value proposition [seen]:** "تحكم بالطلبات بشكل ذكي" (control requests smartly).
- **Mechanism [listing]:** Android AccessibilityService reads offer text (price, time) from other apps' screens and performs the accept action. Processing is stated to be local.
- **Monetisation [seen]:** free trial with live countdown, then subscription [listing: "manage account and subscriptions"].
- **Market signal [listing]:** 1K+ installs, 3.9★ from 11 reviews. Too thin for a review-based analysis.

## 2. Core Entities & Data Model (inferred)
| Entity | Fields | Notes |
|---|---|---|
| UserSettings (local) | auto_accept_enabled bool; min_price_enabled bool; min_price decimal(=2.0); max_pickup_enabled bool; max_pickup_minutes int(=5); price_override_time_enabled bool; price_override_time_value decimal(=0.0); short_trip_override_enabled bool; accept_no_destination_enabled bool; floating_bubble_visible bool | Defaults are what the screenshots show. All stored on-device [listing]. |
| Subscription / Trial | trial_ends_at timestamp; plan; status | Countdown shows days/hours/minutes/seconds remaining. Backend or Play Billing: unknown. |
| TripLog | id; destination; price; accepted_at [inferred]; pickup_minutes [inferred]; rule_matched [inferred] | Per user: lists trips accepted automatically, with destination and price per trip [user-reported]. Other fields are guesses. |
| Account | identity fields | Listing says "account management"; no login screen seen yet. |
Relationships: UserSettings 1—1 User; TripLog N—1 User; Subscription 1—1 User.

## 3. Screens & Layout Structure
**S1. Home / Dashboard [seen]** (vertical scroll, light background, purple brand)
1. Header: purple rounded app icon (lightning bolt), title "أبو كبسة", subtitle "تحكم بالطلبات بشكل ذكي", circular hamburger button (menu).
2. Trial card: green medal icon, "التجربة المجانية", green countdown "0 days 1 hours 48 minutes 17 secs" (English units inside an Arabic UI), chevron → opens something (unseen).
3. Auto-accept hero card (purple gradient): pause icon, label "القبول التلقائي", status "متوقف", toggle (off), white full-width button "تشغيل القبول التلقائي".
4. Row "إمكانية الوصول" / "اضغط لتفعيل الخدمة" + chevron (deep-links to the OS accessibility setting [inferred]).
5. Row "الفقاعة العائمة" / "الفقاعة مخفية" + chevron (floating overlay bubble settings).
6. Section "شروط قبول الطلب" with a hint chip "للتعديل − / +" (how to edit).

**S2. Conditions section [seen]** (continues S1, below the heading)
1. Card "الحد الأدنى للسعر" — sub "أقل سعر مسموح" — toggle (on) — stepper − [2.0] +.
2. Card "أقصى وقت للوصول" — sub "بالدقائق" — toggle (on) — stepper − [5] +.
3. Helper line: "إذا وصل السعر لهذا الرقم يتم تجاهل شرط الوقت".
4. Card (grey = disabled) "سعر تجاوز الوقت" — sub "تجاوز الوقت عند الوصول للسعر" — toggle (off) — stepper − [0.0] +.
5. Card "شرط تجاوز السعر" — sub "قبول طلبات دقيقة أو دقيقتين حتى لو خالفت شروط السعر" — toggle (off).
6. Info box (purple, ⓘ): "مثال: إذا كان سعر التجاوز 7 دنانير، فالطلبات التي سعرها 7 أو أكثر يتم قبولها حتى لو تجاوزت الحد الأقصى للوقت."
7. Card "قبول الطلبات بدون وجهة" — sub "الطلبات بدون سعر تمر على شرط الوقت" — toggle (off) — crossed-out location-pin icon.
8. Outlined full-width button "سجل الرحلات" (history icon) → S3.

**S3. Trip history ("سجل الرحلات") [user-reported, layout not seen]**
- Opened from the outlined button at the bottom of S2.
- A list of trips accepted automatically; each entry shows destination and price.

**Not yet seen (listed so nothing is forgotten):** S3 visual layout and empty state · hamburger menu · trial/subscription screen (via trial card chevron) · accessibility permission flow · floating-bubble settings · login/account · running-state of the hero card · any onboarding/splash.

## 4. User Flows
1. **Turn auto-accept on** [partly seen]: Home → (first time) tap "إمكانية الوصول" → OS accessibility settings → enable service [inferred] → back → tap "تشغيل القبول التلقائي" or flip toggle → status "متوقف" changes to a running state [unseen].
2. **Set conditions** [seen]: scroll to "شروط قبول الطلب" → flip each rule's toggle → adjust value with − / + (field looks editable by typing [inferred]).
3. **Offer evaluation** [inferred from listing + helper texts; short-trip rule confirmed by user]: offer appears in the ride app → read price/time → if price ≥ override price (when enabled) accept regardless of time; else accept only if price ≥ min and pickup ≤ max; offers without a price/destination pass only the time rule when "بدون وجهة" is on; the short-trip rule (pickup 1–2 minutes away) bypasses the min-price rule when "شرط تجاوز السعر" is on [user-confirmed].
4. **Check history** [button seen]: Home → "سجل الرحلات" → list of auto-accepted trips with destination and price [user-reported; layout unseen].
5. **Trial → subscription** [partly seen]: Home → trial card → plans/payment [unseen].
6. **Show/hide floating bubble** [row seen]: Home → "الفقاعة العائمة" → toggle [unseen].

## 5. Component Breakdown
- **AppHeader:** Logo, Title, Subtitle, MenuButton.
- **TrialCard:** Icon, Label, CountdownText (live, 1s tick), Chevron. States: counting · expired [unseen].
- **AutoAcceptCard (hero):** StatusIcon (pause/play), Label, StatusText, Switch, PrimaryButton. States: stopped [seen] · running [unseen] · permission missing [unseen].
- **SettingRow (nav):** Icon, Title, Subtitle (state text), Chevron.
- **SectionHeader:** Title + HintChip.
- **ConditionCard:** Switch, Title, Subtitle, Stepper(−, NumericField, +). States: enabled (white card, green switch) · disabled (grey card, grey outlined switch) · value-editing [unseen].
- **ToggleCard** (no stepper): Switch, Title, Subtitle, optional trailing icon.
- **InfoBox:** ⓘ icon + helper sentence.
- **HelperText:** small grey caption between cards.
- **OutlinedButton:** icon + label ("سجل الرحلات").
- **Switch:** on = green track/knob, off = grey outlined.
- Layout: RTL, single column, rounded cards (~16px radius), light lavender page background, purple primary. (Measure exact tokens with /replica-design later.)

## 6. Out-of-Scope (cannot be replicated clean-room)
- The ride-hailing apps themselves: their offer-screen layouts, text, and any API/data. The accept action depends on them, and I don't know which apps are supported.
- Anything that needs another app's private structure or terms-restricted access.
- Subscription/payment backend specifics (unseen; depends on Play Billing or the developer's server).
- The original's name, logo, icon, copy and brand colours (must be rebranded before launch).

**Risk flags:** (1) Auto-accepting offers inside other apps may violate those apps' terms. (2) Google Play restricts AccessibilityService use; the policy declaration needs care. (3) Play "Data safety" says it may collect personal info, photos/videos and device IDs, which conflicts with the "no data collected" claim, so the data model above is a guess. Check legality and terms before building or selling.

## 7. Open Questions (need next screenshots)
1. Which ride apps does it work with?
2. What do the running state and the trial card's destination look like?
3. ~~What does "شرط تجاوز السعر" measure?~~ Resolved by user: pickup time of 1–2 minutes; such offers are accepted even if they break the min-price rule. (The info box under it still describes "سعر تجاوز الوقت", a UI labelling quirk.)
4. Is there a login, or is identity device-based?
