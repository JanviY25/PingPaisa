# PingPaisa – Affordability Scanner (Android, Kotlin, Compose)

Scan a price tag (or pick screenshots / share a product link), get the price, name and category, and see
whether you can afford it based on your own financial profile. Min Android 10 (API 29), target 35.

## Run it
1. Open this folder in **Android Studio (Ladybug 2024.2 or newer)**. Let Gradle sync (needs internet once).
2. Run on a phone (USB debugging) or an emulator with a camera. Package: `com.pingpaisa.app`.
3. Unit tests: `./gradlew testDebugUnitTest` (43 tests: money, affordability math, price extraction, classification, links).

Optional: copy `local.properties.example` lines into `local.properties` to enable Gemini-assisted
product recognition (`GEMINI_API_KEY`). Not required. The key is compiled into the APK, so use it only for personal builds.

## What works
- Live CameraX preview with on-device price detection inside the white corner brackets.
- Capture → price + product name + category/subcategory. Gallery picker (up to 10 at a time, add more any time, remove any).
- Duplicates are never dropped silently: you are asked.
- Tap any product to edit name, price, category, subcategory, priority. Totals and verdict update instantly.
- Financial profile (menu or "You" tab), stored encrypted on device.
- Verdict card: green "Yes, you can afford it", amber "Buy with caution", red "Better to wait"; "Details" shows the full working.
- Share a product link/text/images from another app to PingPaisa (no extra screen).

## Categories
`assets/india_expense_categorization_dataset.json` supplies the taxonomy. Product types are matched with
`assets/product_keywords.json` (validated against the taxonomy), then on-device image labels, then merchant names.
Anything unmatched becomes **Miscellaneous / Uncategorized**. The dataset has no prices, so prices come only from
the image, product page, shared text or you.

## Affordability model
D = I − E − S_t; S_a = max(0, S − S_e); B_m = α·max(0, D); B_safe = B_m + β·S_a.
Several items: must-haves first, then cheaper first (greedy; simple, not guaranteed optimal). α, β and max wait are
your own settings (presets Cautious/Balanced/Flexible). Without PingPaisa expense history, spending is your typed estimate.

## Known limits
- Home / Stats / AI tabs are placeholders here (a message says they live in the full app).
- Some shopping sites (e.g. Amazon) block automated page reads; then the link is kept and you enter the price.
- Merging into the main app: swap `ExpenseHistoryProvider` in `PingPaisaApp.kt` for real monthly totals.
- This code was written without an Android SDK: pure-logic code is tested, but the Compose/CameraX/ML Kit UI had its
  first compile on your machine. Please report any build error text and it can be fixed quickly.
