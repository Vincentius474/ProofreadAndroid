PROOFREAD for Android

PROOFREAD for Android is an Android-based writing assistant that integrates directly with the text-selection menu, allowing users to correct grammar, punctuation, and sentence structure or rewrite text in different tones across any application. It features AI-powered rewriting, translation, voice dictation, text comparison, editing history, customizable language preferences, and secure API integration with Gemini and Groq to deliver a versatile, privacy-conscious writing experience.

Adds a "Fix grammar" action to the text-selection menu of every app
(WhatsApp, Gmail, Chrome, notes, ...), whatever keyboard you use.

BUILD AND INSTALL
1. Install Android Studio (free): https://developer.android.com/studio
2. File > Open > choose this ProofreadAndroid folder. Accept "Trust project",
   let Gradle sync (it downloads what it needs; internet required, can take a few minutes).
   If asked to update the Android Gradle Plugin or Gradle, you can accept.
3. On your phone: Settings > About phone > tap "Build number" 7 times,
   then Settings > Developer options > turn on USB debugging.
4. Plug the phone in, allow the debugging prompt, press the green Run button.
   Or: Build > Build APK(s), copy app-debug.apk to the phone and open it
   (allow "install unknown apps" when asked).

USE IT
Type text in any app, select it, tap "Fix grammar".
Tip: on newer Android versions it is under the three-dot (...) overflow of the selection menu.

SETTINGS
Open the Proofread app to switch the online deep check on or off.
Text is sent to api.languagetool.org only when the online check is on.

FILES
GrammarFixer.kt      - the correction rules + LanguageTool call
ProcessTextActivity.kt - the selection-menu action
MainActivity.kt      - settings and a test box

UPDATE: "MAKE FORMAL" (AI REWRITE)
1. Go to https://aistudio.google.com, sign in, and create a free API key.
2. Open the Proofread app, paste the key, press "Save AI settings".
3. Select text in any app and tap "Make formal" (or "Fix grammar" for the quick rules).
Choose a style in the app: Formal, Professional or Simple.
If you get a "model not found" message, copy a current free Flash model name from AI Studio
into the "Model name" box (the default is gemini-flash-latest).
Free limits change and are shared across requests, so if you see "Free limit reached", wait a minute.
Privacy: text is sent to Google for this feature only.

BUILT-IN API KEY (optional)
Open local.properties (project root; Android Studio creates it) and add a line:
GEMINI_API_KEY=your_key_here
Then File > Sync Project with Gradle Files and run again. The app uses a key typed in the app first,
and falls back to this built-in one. Anyone who gets your APK can extract an embedded key, so keep the APK
and local.properties private and never upload them (GitHub, WhatsApp groups, etc.).

TROUBLESHOOTING "timed out"
- Open https://generativelanguage.googleapis.com in the phone browser. If it does not load, the network is the problem.
- Try mobile data instead of Wi-Fi (or the other way round).
- Try the model name gemini-flash-lite-latest (faster).

GROQ (FREE, FAST) AS THE AI PROVIDER
1. Create a free account at https://console.groq.com and make a key under API Keys.
2. In the Proofread app choose provider "Groq", paste the key, press "Save AI settings".
   (Optional built-in key: add GROQ_API_KEY=... to local.properties, then sync.)
3. Default model: openai/gpt-oss-120b. If a model is not available on your account, the app asks Groq for the list of models your key can use, switches to one automatically, and remembers it. The button "Show available Groq models" lists them.
You can switch back to Gemini in the same screen. If a model name stops working, copy a current one from the provider's console.

==================== VERSION 2: ALL FEATURES ====================
IN THE APP
- Actions: Fix grammar, Make formal, Make shorter, Make friendlier, Make persuasive, Translate to English, Write as email.
- "Give me 3 options" and "Explain what changed" (AI actions).
- Changed words are highlighted; "Original" / "Changes" button compares.
- History of the last 20 results (saved only on your phone; can be switched off or cleared).
- Dictate (voice) button, and Share-to-Proofread from any app.
- Settings: provider (automatic fallback to the other one), tone, English spelling (South African / British / American),
  your own rules, protected words (never changed), quick action for the tile/shortcut.
- API keys are stored encrypted (Android Keystore).
- If text looks sensitive (ID/card/account numbers, passwords, PINs, API keys) you are asked before anything is sent online.

IN OTHER APPS
- Select text > "Fix grammar", "Make formal" or "Proofread..." (pick any action). AI results are previewed first (can be turned off).
- Quick Settings tile "Fix clipboard": swipe down twice, tap the pencil, drag "Fix clipboard" in. Copy text, tap the tile; the
  fixed text replaces your clipboard. Also available by long-pressing the app icon.
- Proofread keyboard: Settings > "Set up the Proofread keyboard", enable it, then pick it with the keyboard switcher.
  It is a simple keyboard (no suggestions or swipe typing) with Fix / Formal / Undo buttons. Sensitive text is never sent from it.


