#!/usr/bin/env python3
"""
Stage 1 — NLU intent dataset generation harness.

Produces tools/nlu/data/intents.jsonl: the labeled launcher-command dataset that
fine-tunes the 7-class NLU intent classifier (OQ#1). Covers en/ar/tr/ru.

Label contract (argmax index — MUST match NluLabel.kt + train_export.py LABELS):
  0 LAUNCH_APP  1 SEARCH  2 OPEN_SETTINGS  3 SHOW_APPS
  4 HELP        5 OPEN_ASSISTANT           6 UNKNOWN

Usage:
  # Write hand-authored seed set only (no network needed):
  python3 tools/nlu/generate.py --seed-only

  # Expand to full dataset via OpenRouter (API key from env):
  OPENROUTER_API_KEY=sk-or-v1-... python3 tools/nlu/generate.py --expand

  # Expand specific language(s) only:
  OPENROUTER_API_KEY=... python3 tools/nlu/generate.py --expand --lang ar tr

  # Expand specific label(s) only:
  OPENROUTER_API_KEY=... python3 tools/nlu/generate.py --expand --label LAUNCH_APP SEARCH

Design notes:
  - Rule matcher handles: "open X" / "launch X" / "start X" (0.90), "search X" /
    "find X" / "google X" (0.90), "settings" (0.95), "show apps" (0.95), "help" (0.95).
    NLU is only consulted on LOW-CONFIDENCE (<0.50) rule output.
  - Dataset concentrates on the low-confidence neighborhood: bare app names, conversational
    paraphrases, non-English commands, typos, bare topics, ambiguous inputs.
  - Raw natural casing preserved; tokenizer normalizes at train/inference.
    Keeping raw casing preserves the Stage-2 option of switching to the cased mBERT base
    (recommended for Arabic + Turkish by Google) without re-authoring.
  - Model choice prioritised for Arabic + Turkish quality; different models per language
    for natural diversity.
"""

import argparse
import json
import os
import sys
import time
import unicodedata
from pathlib import Path
from typing import Any

try:
    import requests
    _HAS_REQUESTS = True
except ImportError:
    _HAS_REQUESTS = False

# ── Paths ───────────────────────────────────────────────────────────────────────
HERE = Path(__file__).parent
DATA_DIR = HERE / "data"
INTENTS_FILE = DATA_DIR / "intents.jsonl"

# ── Label contract (mirrors NluLabel.kt + train_export.py LABELS list exactly) ──
LABELS = ["LAUNCH_APP", "SEARCH", "OPEN_SETTINGS", "SHOW_APPS", "HELP", "OPEN_ASSISTANT", "UNKNOWN"]
LANGS = ["en", "ar", "tr", "ru"]

# ── Model config (slugs verified from OpenRouter catalog 2026-06-29) ─────────────
# Two models per language for natural diversity in generated examples.
# ar/tr: strongest multilingual frontier models (ar+tr quality is where models diverge).
# en/ru: fast, good-quality models sufficient for these scripts.
MODELS: dict[str, list[str]] = {
    "ar": ["qwen/qwen3-235b-a22b",           "google/gemini-2.5-flash"],
    "tr": ["qwen/qwen3-235b-a22b",           "google/gemini-2.5-flash"],
    # qwen3-30b-a3b returns content=null (hidden thinking mode on this provider route);
    # use gemini-2.5-flash-lite (reliable non-thinking) + gemma-3-27b-it for en/ru diversity.
    "en": ["google/gemini-2.5-flash-lite",   "google/gemma-3-27b-it"],
    "ru": ["google/gemini-2.5-flash-lite",   "google/gemma-3-27b-it"],
}

# Calls per (label × lang) cell: alternates primary/secondary for diversity.
CALLS_PER_CELL = 4   # → ~80 generated + ~10 seeds = ~90 per cell before dedup
EXAMPLES_PER_CALL = 22

OPENROUTER_BASE = "https://openrouter.ai/api/v1"
RATE_LIMIT_SLEEP = 1.2   # seconds between calls (conservative)

# ── Intent descriptions for generation prompts ───────────────────────────────────
INTENT_DESC: dict[str, str] = {
    "LAUNCH_APP": (
        "open a specific mobile app (e.g. Telegram, Spotify, Camera, WhatsApp, Maps, YouTube, "
        "Chrome, Instagram, Calculator, TikTok). Includes bare app names, polite requests, "
        "directional phrases, typos, and conversational phrasings that do NOT start with "
        "'open', 'launch', or 'start'."
    ),
    "SEARCH": (
        "search for information on the web: weather, news, recipes, prices, directions, "
        "questions (who/what/where/how), bare topics, or phrasing without the word 'search'. "
        "Includes question-form utterances and bare topic queries."
    ),
    "OPEN_SETTINGS": (
        "open the phone's system settings: WiFi, Bluetooth, brightness, sound, notifications, "
        "display, battery. Includes bare 'settings' in the target language, contextual "
        "phrasings ('I want to change my WiFi'), and mentions of specific setting categories."
    ),
    "SHOW_APPS": (
        "see all installed apps, open the app drawer, or find an app — any phrasing for "
        "'show me all apps / app list / app drawer'. Includes bare 'apps' in the target language, "
        "questions about where apps are, and requests to list everything installed."
    ),
    "HELP": (
        "get help understanding how to use the launcher: what commands work, what can be done, "
        "how it works. Includes expressions of confusion, requests for a guide, and asking "
        "what the launcher is capable of."
    ),
    "OPEN_ASSISTANT": (
        "open the built-in AI assistant / switch to AI chat mode. Includes bare 'assistant' / "
        "AI / chatbot references in the target language, requests to chat with AI, or switch to "
        "AI mode — NOT general app launches or web searches."
    ),
    "UNKNOWN": (
        "something that is NOT a launcher command: chit-chat ('good morning', 'I'm hungry'), "
        "alarms ('set alarm for 7am'), phone calls ('call mom'), math ('2+2'), trivia "
        "('capital of France'), translations ('translate hello'), music requests WITHOUT a "
        "named app ('play music'), gibberish/random characters. Variety is critical — "
        "an UNKNOWN-heavy model that never escapes defeats the system's safety design."
    ),
}

# ── Language metadata for prompts ─────────────────────────────────────────────────
LANG_META: dict[str, dict[str, str]] = {
    "en": {"name": "English", "notes": ""},
    "ar": {
        "name": "Arabic",
        "notes": (
            "Use SPOKEN DIALECT (Egyptian, Gulf/Khaleeji, or Levantine) — NOT formal MSA. "
            "Real users type how they speak. Include clitic prefixes/suffixes natural in dialect. "
            "RTL script."
        ),
    },
    "tr": {
        "name": "Turkish",
        "notes": (
            "Turkish is agglutinative — suffixes stack. Produce grammatically natural Turkish "
            "with real suffix combinations (e.g. 'whatsapp'ı aç', 'uygulamalarıma bak'). "
            "Use informal/colloquial register as well as polite forms."
        ),
    },
    "ru": {
        "name": "Russian",
        "notes": "Cyrillic script. Use informal colloquial register as well as neutral register.",
    },
}


# ── Seed data ────────────────────────────────────────────────────────────────────
# Hand-authored, native-language, reviewed=True.  Raw casing preserved.
# Focus: cases where rule confidence < 0.50 (NLU earns its place) — bare names,
# conversational paraphrases, non-English commands, typos, ambiguous phrasings.
# Rule-canonical forms (open X → 0.90, settings → 0.95, help → 0.95) deliberately
# under-represented; the dataset earns value from the ambiguous neighborhood.
SEED_EXAMPLES: list[dict[str, Any]] = [
    # ── LAUNCH_APP ────────────────────────────────────────────────────────────────
    # Rule nails: "open X"(0.90), "launch X"(0.90), "start X"(0.90) → NLU never consulted.
    # Value: bare names, conversational, non-English forms, typos.
    {"text": "telegram",                          "label": "LAUNCH_APP", "lang": "en"},
    {"text": "spotify please",                    "label": "LAUNCH_APP", "lang": "en"},
    {"text": "can you pull up whatsapp",          "label": "LAUNCH_APP", "lang": "en"},
    {"text": "I need to check maps",             "label": "LAUNCH_APP", "lang": "en"},
    {"text": "let me into youtube",               "label": "LAUNCH_APP", "lang": "en"},
    {"text": "fire up the camera",                "label": "LAUNCH_APP", "lang": "en"},
    {"text": "bring up chrome for me",            "label": "LAUNCH_APP", "lang": "en"},
    {"text": "take me to instagram",              "label": "LAUNCH_APP", "lang": "en"},
    {"text": "I want to use calculator",          "label": "LAUNCH_APP", "lang": "en"},
    {"text": "telegran please",                   "label": "LAUNCH_APP", "lang": "en"},  # typo

    {"text": "واتساب",                            "label": "LAUNCH_APP", "lang": "ar"},
    {"text": "تيليغرام",                          "label": "LAUNCH_APP", "lang": "ar"},
    {"text": "افتحلي واتساب",                    "label": "LAUNCH_APP", "lang": "ar"},
    {"text": "نبي ندخل يوتيوب",                  "label": "LAUNCH_APP", "lang": "ar"},
    {"text": "وديني على الخرائط",                "label": "LAUNCH_APP", "lang": "ar"},
    {"text": "كاميرا من فضلك",                  "label": "LAUNCH_APP", "lang": "ar"},
    {"text": "أبي أفتح سبوتيفاي",               "label": "LAUNCH_APP", "lang": "ar"},
    {"text": "ممكن تشغّل الكروم",               "label": "LAUNCH_APP", "lang": "ar"},
    {"text": "روح على تيك توك",                  "label": "LAUNCH_APP", "lang": "ar"},
    {"text": "عايز أفتح الحاسبة",               "label": "LAUNCH_APP", "lang": "ar"},

    {"text": "telegram",                          "label": "LAUNCH_APP", "lang": "tr"},
    {"text": "whatsapp'ı aç",                     "label": "LAUNCH_APP", "lang": "tr"},
    {"text": "spotify'a gitmek istiyorum",         "label": "LAUNCH_APP", "lang": "tr"},
    {"text": "haritaları açar mısın",             "label": "LAUNCH_APP", "lang": "tr"},
    {"text": "kamerayla fotoğraf çekmek istiyorum", "label": "LAUNCH_APP", "lang": "tr"},
    {"text": "YouTube açılsın",                   "label": "LAUNCH_APP", "lang": "tr"},
    {"text": "hesap makinesi lütfen",             "label": "LAUNCH_APP", "lang": "tr"},
    {"text": "instagram'a geçelim",               "label": "LAUNCH_APP", "lang": "tr"},
    {"text": "telegrami aç",                      "label": "LAUNCH_APP", "lang": "tr"},  # missing apostrophe
    {"text": "chrome'u başlat",                   "label": "LAUNCH_APP", "lang": "tr"},

    {"text": "телеграм",                          "label": "LAUNCH_APP", "lang": "ru"},
    {"text": "открой ватсап",                     "label": "LAUNCH_APP", "lang": "ru"},
    {"text": "хочу зайти в ютуб",                "label": "LAUNCH_APP", "lang": "ru"},
    {"text": "запусти камеру пожалуйста",         "label": "LAUNCH_APP", "lang": "ru"},
    {"text": "мне нужен спотифай",               "label": "LAUNCH_APP", "lang": "ru"},
    {"text": "карты открой",                      "label": "LAUNCH_APP", "lang": "ru"},
    {"text": "можешь открыть хром",               "label": "LAUNCH_APP", "lang": "ru"},
    {"text": "калькулятор нужен",                "label": "LAUNCH_APP", "lang": "ru"},
    {"text": "инстаграм пожалуйста",             "label": "LAUNCH_APP", "lang": "ru"},
    {"text": "вотсап",                            "label": "LAUNCH_APP", "lang": "ru"},

    # ── SEARCH ────────────────────────────────────────────────────────────────────
    # Rule: "search X"(0.90), "find X"(0.90), "google X"(0.90).
    # Value: question-form, bare topics, need-phrasing, non-English.
    {"text": "what is the weather today",         "label": "SEARCH", "lang": "en"},
    {"text": "pizza near me",                     "label": "SEARCH", "lang": "en"},
    {"text": "who is the president of france",    "label": "SEARCH", "lang": "en"},
    {"text": "python tutorials",                  "label": "SEARCH", "lang": "en"},
    {"text": "cheap flights to istanbul",         "label": "SEARCH", "lang": "en"},
    {"text": "how do I fix this error",           "label": "SEARCH", "lang": "en"},
    {"text": "latest news",                       "label": "SEARCH", "lang": "en"},
    {"text": "cat videos on youtube",             "label": "SEARCH", "lang": "en"},
    {"text": "what time does the pharmacy close", "label": "SEARCH", "lang": "en"},
    {"text": "best restaurants in dubai",         "label": "SEARCH", "lang": "en"},

    {"text": "دور على مطاعم قريب مني",           "label": "SEARCH", "lang": "ar"},
    {"text": "كيف أصلح هذا الخطأ",              "label": "SEARCH", "lang": "ar"},
    {"text": "أخبار اليوم",                      "label": "SEARCH", "lang": "ar"},
    {"text": "وين أقرب صيدلية",                 "label": "SEARCH", "lang": "ar"},
    {"text": "رحلات رخيصة إلى إسطنبول",         "label": "SEARCH", "lang": "ar"},
    {"text": "أسعار الدولار اليوم",              "label": "SEARCH", "lang": "ar"},
    {"text": "وصفة بسبوسة",                      "label": "SEARCH", "lang": "ar"},
    {"text": "كيف الطقس غداً",                  "label": "SEARCH", "lang": "ar"},
    {"text": "ابحثلي عن كورسات بايثون",         "label": "SEARCH", "lang": "ar"},
    {"text": "مين اخترع التليفون",              "label": "SEARCH", "lang": "ar"},

    {"text": "bugün hava nasıl",                  "label": "SEARCH", "lang": "tr"},
    {"text": "yakınımda pizza nerede",            "label": "SEARCH", "lang": "tr"},
    {"text": "istanbul'a ucuz uçuş",              "label": "SEARCH", "lang": "tr"},
    {"text": "python öğrenmek için kaynak",       "label": "SEARCH", "lang": "tr"},
    {"text": "en yakın eczane nerede",            "label": "SEARCH", "lang": "tr"},
    {"text": "dolar kuru bugün",                  "label": "SEARCH", "lang": "tr"},
    {"text": "Türk filmleri 2024",                "label": "SEARCH", "lang": "tr"},
    {"text": "bu hatayı nasıl düzeltebilirim",   "label": "SEARCH", "lang": "tr"},
    {"text": "baklava tarifi",                    "label": "SEARCH", "lang": "tr"},
    {"text": "yarın hava durumu",                 "label": "SEARCH", "lang": "tr"},

    {"text": "погода завтра",                     "label": "SEARCH", "lang": "ru"},
    {"text": "пицца рядом со мной",              "label": "SEARCH", "lang": "ru"},
    {"text": "дешёвые билеты в стамбул",         "label": "SEARCH", "lang": "ru"},
    {"text": "курс доллара сегодня",             "label": "SEARCH", "lang": "ru"},
    {"text": "рецепт борща",                     "label": "SEARCH", "lang": "ru"},
    {"text": "ближайшая аптека",                 "label": "SEARCH", "lang": "ru"},
    {"text": "как исправить эту ошибку",         "label": "SEARCH", "lang": "ru"},
    {"text": "последние новости",                "label": "SEARCH", "lang": "ru"},
    {"text": "кто изобрёл телефон",             "label": "SEARCH", "lang": "ru"},
    {"text": "туториалы по питону",              "label": "SEARCH", "lang": "ru"},

    # ── OPEN_SETTINGS ─────────────────────────────────────────────────────────────
    # Rule: "settings"(0.95), "launcher settings"(0.95).
    # Value: bare "settings" in non-English, contextual phrasings, setting categories.
    {"text": "I need to change my wifi password",  "label": "OPEN_SETTINGS", "lang": "en"},
    {"text": "phone settings",                     "label": "OPEN_SETTINGS", "lang": "en"},
    {"text": "system settings please",             "label": "OPEN_SETTINGS", "lang": "en"},
    {"text": "adjust brightness",                  "label": "OPEN_SETTINGS", "lang": "en"},
    {"text": "where is the sound setting",         "label": "OPEN_SETTINGS", "lang": "en"},
    {"text": "I want to check my bluetooth",       "label": "OPEN_SETTINGS", "lang": "en"},
    {"text": "take me to device settings",         "label": "OPEN_SETTINGS", "lang": "en"},
    {"text": "notification settings",              "label": "OPEN_SETTINGS", "lang": "en"},
    {"text": "I need to turn on wifi",             "label": "OPEN_SETTINGS", "lang": "en"},
    {"text": "display settings",                   "label": "OPEN_SETTINGS", "lang": "en"},

    {"text": "الإعدادات",                         "label": "OPEN_SETTINGS", "lang": "ar"},
    {"text": "أبي أفتح الإعدادات",              "label": "OPEN_SETTINGS", "lang": "ar"},
    {"text": "إعدادات الواي فاي وين",           "label": "OPEN_SETTINGS", "lang": "ar"},
    {"text": "غيّر لي إعدادات الصوت",           "label": "OPEN_SETTINGS", "lang": "ar"},
    {"text": "خذني على إعدادات الجهاز",         "label": "OPEN_SETTINGS", "lang": "ar"},
    {"text": "إعدادات الإشعارات",               "label": "OPEN_SETTINGS", "lang": "ar"},
    {"text": "أبي أشغّل البلوتوث",             "label": "OPEN_SETTINGS", "lang": "ar"},
    {"text": "عايز أفتح الإعدادات",             "label": "OPEN_SETTINGS", "lang": "ar"},
    {"text": "روح على إعدادات الجهاز",          "label": "OPEN_SETTINGS", "lang": "ar"},
    {"text": "وين إعدادات الشاشة",              "label": "OPEN_SETTINGS", "lang": "ar"},

    {"text": "ayarlar",                            "label": "OPEN_SETTINGS", "lang": "tr"},
    {"text": "telefon ayarlarına git",             "label": "OPEN_SETTINGS", "lang": "tr"},
    {"text": "ses ayarlarını değiştirmek istiyorum", "label": "OPEN_SETTINGS", "lang": "tr"},
    {"text": "wifi şifremi değiştirmek istiyorum", "label": "OPEN_SETTINGS", "lang": "tr"},
    {"text": "bluetooth'u aç",                    "label": "OPEN_SETTINGS", "lang": "tr"},
    {"text": "ekran parlaklığını ayarla",         "label": "OPEN_SETTINGS", "lang": "tr"},
    {"text": "bildirim ayarları nerede",           "label": "OPEN_SETTINGS", "lang": "tr"},
    {"text": "sistem ayarlarını aç",              "label": "OPEN_SETTINGS", "lang": "tr"},
    {"text": "ayarlar menüsüne gitmek istiyorum", "label": "OPEN_SETTINGS", "lang": "tr"},
    {"text": "wifi'yi açmak istiyorum",           "label": "OPEN_SETTINGS", "lang": "tr"},

    {"text": "настройки",                         "label": "OPEN_SETTINGS", "lang": "ru"},
    {"text": "открой настройки телефона",         "label": "OPEN_SETTINGS", "lang": "ru"},
    {"text": "где настройки вайфай",             "label": "OPEN_SETTINGS", "lang": "ru"},
    {"text": "настройки звука пожалуйста",        "label": "OPEN_SETTINGS", "lang": "ru"},
    {"text": "хочу включить блютуз",             "label": "OPEN_SETTINGS", "lang": "ru"},
    {"text": "настройки яркости",                "label": "OPEN_SETTINGS", "lang": "ru"},
    {"text": "где настройки уведомлений",        "label": "OPEN_SETTINGS", "lang": "ru"},
    {"text": "мне нужны настройки",             "label": "OPEN_SETTINGS", "lang": "ru"},
    {"text": "зайди в системные настройки",      "label": "OPEN_SETTINGS", "lang": "ru"},
    {"text": "настройки вай-фай",               "label": "OPEN_SETTINGS", "lang": "ru"},

    # ── SHOW_APPS ─────────────────────────────────────────────────────────────────
    # Rule: "show apps"(0.95), "show all apps"(0.95).
    # Value: bare "apps" in non-English, alternative phrasings, "app drawer", "find an app".
    {"text": "all apps",                           "label": "SHOW_APPS", "lang": "en"},
    {"text": "I want to see all my apps",          "label": "SHOW_APPS", "lang": "en"},
    {"text": "app drawer please",                  "label": "SHOW_APPS", "lang": "en"},
    {"text": "where is my app grid",               "label": "SHOW_APPS", "lang": "en"},
    {"text": "can I see everything installed",     "label": "SHOW_APPS", "lang": "en"},
    {"text": "bring up all my apps",               "label": "SHOW_APPS", "lang": "en"},
    {"text": "I need to find an app",              "label": "SHOW_APPS", "lang": "en"},
    {"text": "open the app list",                  "label": "SHOW_APPS", "lang": "en"},
    {"text": "everything on my phone",             "label": "SHOW_APPS", "lang": "en"},
    {"text": "app drawer",                         "label": "SHOW_APPS", "lang": "en"},

    {"text": "التطبيقات",                         "label": "SHOW_APPS", "lang": "ar"},
    {"text": "أرني كل التطبيقات",               "label": "SHOW_APPS", "lang": "ar"},
    {"text": "وين كل تطبيقاتي",                 "label": "SHOW_APPS", "lang": "ar"},
    {"text": "قائمة التطبيقات",                  "label": "SHOW_APPS", "lang": "ar"},
    {"text": "أبي أشوف كل التطبيقات المثبتة",  "label": "SHOW_APPS", "lang": "ar"},
    {"text": "عرّض لي كل شيء",                  "label": "SHOW_APPS", "lang": "ar"},
    {"text": "أين سلة التطبيقات",               "label": "SHOW_APPS", "lang": "ar"},
    {"text": "كيف أشوف كل التطبيقات",           "label": "SHOW_APPS", "lang": "ar"},
    {"text": "افتح قائمة التطبيقات",            "label": "SHOW_APPS", "lang": "ar"},
    {"text": "شوف التطبيقات",                    "label": "SHOW_APPS", "lang": "ar"},

    {"text": "uygulamalar",                        "label": "SHOW_APPS", "lang": "tr"},
    {"text": "tüm uygulamaları göster",           "label": "SHOW_APPS", "lang": "tr"},
    {"text": "hangi uygulamalar yüklü",           "label": "SHOW_APPS", "lang": "tr"},
    {"text": "uygulama çekmecesini aç",           "label": "SHOW_APPS", "lang": "tr"},
    {"text": "bütün uygulamalarımı görmek istiyorum", "label": "SHOW_APPS", "lang": "tr"},
    {"text": "yüklü uygulamaları listele",        "label": "SHOW_APPS", "lang": "tr"},
    {"text": "telefonumdaki her şeyi göster",     "label": "SHOW_APPS", "lang": "tr"},
    {"text": "uygulama listememi açar mısın",     "label": "SHOW_APPS", "lang": "tr"},
    {"text": "app drawer nerede",                  "label": "SHOW_APPS", "lang": "tr"},
    {"text": "tüm applerimi göster",              "label": "SHOW_APPS", "lang": "tr"},

    {"text": "все приложения",                    "label": "SHOW_APPS", "lang": "ru"},
    {"text": "покажи все мои приложения",         "label": "SHOW_APPS", "lang": "ru"},
    {"text": "список приложений",                 "label": "SHOW_APPS", "lang": "ru"},
    {"text": "открой ящик приложений",            "label": "SHOW_APPS", "lang": "ru"},
    {"text": "где все приложения на телефоне",    "label": "SHOW_APPS", "lang": "ru"},
    {"text": "хочу посмотреть все установленные приложения", "label": "SHOW_APPS", "lang": "ru"},
    {"text": "покажи всё что установлено",        "label": "SHOW_APPS", "lang": "ru"},
    {"text": "выведи список приложений",          "label": "SHOW_APPS", "lang": "ru"},
    {"text": "мне нужно найти одно приложение",  "label": "SHOW_APPS", "lang": "ru"},
    {"text": "приложения",                        "label": "SHOW_APPS", "lang": "ru"},

    # ── HELP ──────────────────────────────────────────────────────────────────────
    # Rule: "help"(0.95). Value: variations, confusion expressions, questions, non-English.
    {"text": "I don't know how to use this",      "label": "HELP", "lang": "en"},
    {"text": "what can you do",                   "label": "HELP", "lang": "en"},
    {"text": "how does this launcher work",       "label": "HELP", "lang": "en"},
    {"text": "show me what commands work",        "label": "HELP", "lang": "en"},
    {"text": "guide me",                          "label": "HELP", "lang": "en"},
    {"text": "what can I type here",              "label": "HELP", "lang": "en"},
    {"text": "I'm confused",                      "label": "HELP", "lang": "en"},
    {"text": "how do I use you",                  "label": "HELP", "lang": "en"},
    {"text": "can you help me",                   "label": "HELP", "lang": "en"},
    {"text": "what are the available commands",   "label": "HELP", "lang": "en"},

    {"text": "ساعدني",                            "label": "HELP", "lang": "ar"},
    {"text": "مساعدة",                            "label": "HELP", "lang": "ar"},
    {"text": "كيف أستخدم هذا المشغّل",           "label": "HELP", "lang": "ar"},
    {"text": "ما اللي تقدر تسويه",               "label": "HELP", "lang": "ar"},
    {"text": "وش أكتب هنا",                      "label": "HELP", "lang": "ar"},
    {"text": "ما أفهم كيف يشتغل",               "label": "HELP", "lang": "ar"},
    {"text": "الأوامر المتاحة إيش هي",           "label": "HELP", "lang": "ar"},
    {"text": "إزاي أستخدم التطبيق ده",          "label": "HELP", "lang": "ar"},
    {"text": "محتاج مساعدة",                     "label": "HELP", "lang": "ar"},
    {"text": "أشرحلي كيف يشتغل",               "label": "HELP", "lang": "ar"},

    {"text": "yardım",                            "label": "HELP", "lang": "tr"},
    {"text": "bunu nasıl kullanırım",             "label": "HELP", "lang": "tr"},
    {"text": "ne yapabilirim burada",             "label": "HELP", "lang": "tr"},
    {"text": "ne yapabilirsin",                   "label": "HELP", "lang": "tr"},
    {"text": "komutlar neler",                    "label": "HELP", "lang": "tr"},
    {"text": "nasıl çalışıyor bu başlatıcı",      "label": "HELP", "lang": "tr"},
    {"text": "kullanım kılavuzu var mı",          "label": "HELP", "lang": "tr"},
    {"text": "ne yazmalıyım buraya",              "label": "HELP", "lang": "tr"},
    {"text": "anlayamadım nasıl kullanacağım",    "label": "HELP", "lang": "tr"},
    {"text": "yardım et bana",                    "label": "HELP", "lang": "tr"},

    {"text": "помоги мне",                        "label": "HELP", "lang": "ru"},
    {"text": "помощь",                            "label": "HELP", "lang": "ru"},
    {"text": "как это использовать",              "label": "HELP", "lang": "ru"},
    {"text": "что ты умеешь делать",              "label": "HELP", "lang": "ru"},
    {"text": "какие команды можно вводить",       "label": "HELP", "lang": "ru"},
    {"text": "как работает этот лаунчер",         "label": "HELP", "lang": "ru"},
    {"text": "не понимаю как пользоваться",       "label": "HELP", "lang": "ru"},
    {"text": "подскажи что делать",               "label": "HELP", "lang": "ru"},
    {"text": "нужна помощь с использованием",     "label": "HELP", "lang": "ru"},
    {"text": "что тут можно делать",              "label": "HELP", "lang": "ru"},

    # ── OPEN_ASSISTANT ────────────────────────────────────────────────────────────
    # Rule: no SIMPLE_COMMAND for "assistant"; "open assistant" → LaunchAppIntent(query="assistant") at 0.90.
    # Value: bare "assistant"/AI references, conversational phrasings, non-English.
    {"text": "assistant",                          "label": "OPEN_ASSISTANT", "lang": "en"},
    {"text": "AI please",                          "label": "OPEN_ASSISTANT", "lang": "en"},
    {"text": "I want to chat with the AI",        "label": "OPEN_ASSISTANT", "lang": "en"},
    {"text": "talk to AI",                         "label": "OPEN_ASSISTANT", "lang": "en"},
    {"text": "switch to the AI assistant",         "label": "OPEN_ASSISTANT", "lang": "en"},
    {"text": "let me use the chatbot",             "label": "OPEN_ASSISTANT", "lang": "en"},
    {"text": "I need the AI",                      "label": "OPEN_ASSISTANT", "lang": "en"},
    {"text": "go to AI chat",                      "label": "OPEN_ASSISTANT", "lang": "en"},
    {"text": "take me to the assistant",           "label": "OPEN_ASSISTANT", "lang": "en"},
    {"text": "AI mode",                            "label": "OPEN_ASSISTANT", "lang": "en"},

    {"text": "المساعد",                           "label": "OPEN_ASSISTANT", "lang": "ar"},
    {"text": "الذكاء الاصطناعي",                 "label": "OPEN_ASSISTANT", "lang": "ar"},
    {"text": "أبي أتكلم مع الذكاء الاصطناعي",  "label": "OPEN_ASSISTANT", "lang": "ar"},
    {"text": "افتح المساعد الذكي",               "label": "OPEN_ASSISTANT", "lang": "ar"},
    {"text": "وديني على المحادثة مع الذكاء",   "label": "OPEN_ASSISTANT", "lang": "ar"},
    {"text": "أبي أشوف المساعد",               "label": "OPEN_ASSISTANT", "lang": "ar"},
    {"text": "محادثة مع الذكاء الاصطناعي",     "label": "OPEN_ASSISTANT", "lang": "ar"},
    {"text": "الشات بوت",                        "label": "OPEN_ASSISTANT", "lang": "ar"},
    {"text": "عايز أكلم المساعد",               "label": "OPEN_ASSISTANT", "lang": "ar"},
    {"text": "روح على المساعد",                  "label": "OPEN_ASSISTANT", "lang": "ar"},

    {"text": "asistan",                            "label": "OPEN_ASSISTANT", "lang": "tr"},
    {"text": "yapay zeka ile konuşmak istiyorum",  "label": "OPEN_ASSISTANT", "lang": "tr"},
    {"text": "AI asistanı aç",                    "label": "OPEN_ASSISTANT", "lang": "tr"},
    {"text": "sohbet moduna geç",                 "label": "OPEN_ASSISTANT", "lang": "tr"},
    {"text": "yapay zeka ile sohbet",             "label": "OPEN_ASSISTANT", "lang": "tr"},
    {"text": "asistanla konuşabilir miyim",        "label": "OPEN_ASSISTANT", "lang": "tr"},
    {"text": "AI moduna geç",                     "label": "OPEN_ASSISTANT", "lang": "tr"},
    {"text": "chatbot'u aç",                      "label": "OPEN_ASSISTANT", "lang": "tr"},
    {"text": "yapay zeka asistanına git",         "label": "OPEN_ASSISTANT", "lang": "tr"},
    {"text": "AI ile konuşmak istiyorum",         "label": "OPEN_ASSISTANT", "lang": "tr"},

    {"text": "ассистент",                         "label": "OPEN_ASSISTANT", "lang": "ru"},
    {"text": "хочу поговорить с ИИ",             "label": "OPEN_ASSISTANT", "lang": "ru"},
    {"text": "открой ИИ ассистента",             "label": "OPEN_ASSISTANT", "lang": "ru"},
    {"text": "переключи на чат с ИИ",            "label": "OPEN_ASSISTANT", "lang": "ru"},
    {"text": "давай поговорим с помощником",      "label": "OPEN_ASSISTANT", "lang": "ru"},
    {"text": "открой чат с искусственным интеллектом", "label": "OPEN_ASSISTANT", "lang": "ru"},
    {"text": "мне нужен ИИ ассистент",          "label": "OPEN_ASSISTANT", "lang": "ru"},
    {"text": "ИИ пожалуйста",                    "label": "OPEN_ASSISTANT", "lang": "ru"},
    {"text": "режим чата с ИИ",                 "label": "OPEN_ASSISTANT", "lang": "ru"},
    {"text": "чатбот",                            "label": "OPEN_ASSISTANT", "lang": "ru"},

    # ── UNKNOWN ───────────────────────────────────────────────────────────────────
    # Out-of-scope for a phone launcher.  Must be DIVERSE — an under-diverse UNKNOWN means
    # the model force-fits everything into real intents and the escape never fires.
    # Varieties: alarms, calls, math, trivia, translations, social greetings, music without
    # a named app, gibberish/random characters.
    {"text": "what time is it",                   "label": "UNKNOWN", "lang": "en"},
    {"text": "set an alarm for 7am",              "label": "UNKNOWN", "lang": "en"},
    {"text": "call my mom",                       "label": "UNKNOWN", "lang": "en"},
    {"text": "tell me a joke",                    "label": "UNKNOWN", "lang": "en"},
    {"text": "what is 2 plus 2",                  "label": "UNKNOWN", "lang": "en"},
    {"text": "translate hello to french",         "label": "UNKNOWN", "lang": "en"},
    {"text": "I'm hungry",                        "label": "UNKNOWN", "lang": "en"},
    {"text": "good morning",                      "label": "UNKNOWN", "lang": "en"},
    {"text": "asdfghjkl",                         "label": "UNKNOWN", "lang": "en"},
    {"text": "what is the capital of france",     "label": "UNKNOWN", "lang": "en"},
    {"text": "write me an email",                 "label": "UNKNOWN", "lang": "en"},
    {"text": "play music",                        "label": "UNKNOWN", "lang": "en"},

    {"text": "كم الساعة الآن",                   "label": "UNKNOWN", "lang": "ar"},
    {"text": "حط منبه الساعة سبعة الصبح",        "label": "UNKNOWN", "lang": "ar"},
    {"text": "اتصل بأمي",                        "label": "UNKNOWN", "lang": "ar"},
    {"text": "قول لي نكتة",                      "label": "UNKNOWN", "lang": "ar"},
    {"text": "كم يساوي 2 زائد 2",               "label": "UNKNOWN", "lang": "ar"},
    {"text": "ترجم مرحبا للإنجليزي",             "label": "UNKNOWN", "lang": "ar"},
    {"text": "أنا جوعان",                        "label": "UNKNOWN", "lang": "ar"},
    {"text": "صباح الخير",                       "label": "UNKNOWN", "lang": "ar"},
    {"text": "ما عاصمة فرنسا",                   "label": "UNKNOWN", "lang": "ar"},
    {"text": "اكتب لي إيميل",                    "label": "UNKNOWN", "lang": "ar"},
    {"text": "شغّل لي أغنية",                   "label": "UNKNOWN", "lang": "ar"},
    {"text": "12345",                             "label": "UNKNOWN", "lang": "ar"},

    {"text": "saat kaç",                          "label": "UNKNOWN", "lang": "tr"},
    {"text": "sabah 7 için alarm kur",            "label": "UNKNOWN", "lang": "tr"},
    {"text": "annemi ara",                        "label": "UNKNOWN", "lang": "tr"},
    {"text": "bana şaka anlat",                   "label": "UNKNOWN", "lang": "tr"},
    {"text": "iki artı iki kaç eder",             "label": "UNKNOWN", "lang": "tr"},
    {"text": "merhaba'yı ingilizceye çevir",      "label": "UNKNOWN", "lang": "tr"},
    {"text": "acıktım",                           "label": "UNKNOWN", "lang": "tr"},
    {"text": "günaydın",                          "label": "UNKNOWN", "lang": "tr"},
    {"text": "fransa'nın başkenti neresi",         "label": "UNKNOWN", "lang": "tr"},
    {"text": "e-posta yaz",                       "label": "UNKNOWN", "lang": "tr"},
    {"text": "müzik çal",                         "label": "UNKNOWN", "lang": "tr"},
    {"text": "asdfgh",                            "label": "UNKNOWN", "lang": "tr"},

    {"text": "который час",                       "label": "UNKNOWN", "lang": "ru"},
    {"text": "поставь будильник на семь утра",    "label": "UNKNOWN", "lang": "ru"},
    {"text": "позвони маме",                      "label": "UNKNOWN", "lang": "ru"},
    {"text": "расскажи анекдот",                  "label": "UNKNOWN", "lang": "ru"},
    {"text": "сколько будет два плюс два",        "label": "UNKNOWN", "lang": "ru"},
    {"text": "переведи привет на английский",     "label": "UNKNOWN", "lang": "ru"},
    {"text": "хочу есть",                         "label": "UNKNOWN", "lang": "ru"},
    {"text": "доброе утро",                       "label": "UNKNOWN", "lang": "ru"},
    {"text": "столица франции",                   "label": "UNKNOWN", "lang": "ru"},
    {"text": "напиши письмо",                     "label": "UNKNOWN", "lang": "ru"},
    {"text": "включи музыку",                     "label": "UNKNOWN", "lang": "ru"},
    {"text": "фыывавыа",                          "label": "UNKNOWN", "lang": "ru"},
]


# ── Generation helpers ────────────────────────────────────────────────────────────

def _normalize(text: str) -> str:
    """NFC-normalise for dedup; does NOT lowercase (raw casing is preserved in the dataset)."""
    return unicodedata.normalize("NFC", text).strip()


def _build_prompt(label: str, lang: str, seed_texts: list[str], n: int) -> str:
    lang_name = LANG_META[lang]["name"]
    lang_notes = LANG_META[lang]["notes"]
    desc = INTENT_DESC[label]

    examples_block = "\n".join(f"- {t}" for t in seed_texts[:8])

    notes_block = f"\nIMPORTANT language notes: {lang_notes}" if lang_notes else ""

    return (
        f"You are building training data for a multilingual intent classifier "
        f"for an Android phone launcher app.\n\n"
        f"TASK: Generate {n} short, natural utterances in {lang_name} that a native "
        f"{lang_name} speaker would actually type or say when they want to: {desc}\n\n"
        f"RULES:\n"
        f"- Write ONLY in {lang_name} — do NOT translate from English\n"
        f"- Idiomatic, native phrasing — how real people speak/type, NOT textbook language\n"
        f"- Include realistic occasional typos in some utterances\n"
        f"- Vary length (1 to 12 words) and register (casual, polite, brief)\n"
        f"- Each utterance on its own line, no numbering, no labels, no punctuation at ends\n"
        f"- Produce DISTINCT utterances — no near-paraphrases of each other"
        f"{notes_block}\n\n"
        f"Examples of the style (in {lang_name}):\n{examples_block}\n\n"
        f"OUTPUT exactly {n} utterances, one per line:"
    )


def _call_openrouter(prompt: str, model: str, api_key: str, n: int) -> list[str]:
    """One OpenRouter chat-completion call; returns raw output lines."""
    if not _HAS_REQUESTS:
        raise RuntimeError("pip install requests")

    url = f"{OPENROUTER_BASE}/chat/completions"
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "max_tokens": n * 30,
        "temperature": 0.9,
    }
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    resp = requests.post(url, json=payload, headers=headers, timeout=120)
    if resp.status_code != 200:
        raise RuntimeError(f"OpenRouter {resp.status_code}: {resp.text[:200]}")
    data = resp.json()
    msg = data["choices"][0]["message"]
    content = msg.get("content") or msg.get("reasoning") or ""
    if not content:
        raise RuntimeError(
            f"Model returned empty/null content (possible hidden thinking mode). "
            f"Response keys: {list(msg.keys())}. Switch to a non-thinking model."
        )
    lines = [ln.strip() for ln in content.splitlines() if ln.strip()]
    # Strip leading list markers if the model added them despite instructions
    cleaned = []
    for ln in lines:
        # Remove "1. ", "- ", "• " prefixes
        for prefix in ("- ", "• ", "* "):
            if ln.startswith(prefix):
                ln = ln[len(prefix):]
                break
        if ln and ln[0].isdigit() and len(ln) > 2 and ln[1] in ".):":
            ln = ln[2:].lstrip()
        if ln:
            cleaned.append(ln)
    return cleaned


def _generate_cell(
    label: str,
    lang: str,
    existing_texts: set[str],
    api_key: str,
    n_calls: int,
    n_per_call: int,
) -> list[dict[str, Any]]:
    """Generate examples for one (label, lang) cell across multiple models."""
    seed_texts = [
        e["text"] for e in SEED_EXAMPLES
        if e["label"] == label and e["lang"] == lang
    ]
    models = MODELS[lang]
    results: list[dict[str, Any]] = []
    seen: set[str] = set(existing_texts)

    for call_idx in range(n_calls):
        model = models[call_idx % len(models)]
        model_short = model.split("/")[-1][:20]
        family_key = f"gen_{label}_{lang}_{model_short}_{call_idx}"

        prompt = _build_prompt(label, lang, seed_texts, n_per_call)
        print(f"  [{label}/{lang}] call {call_idx+1}/{n_calls} model={model} ...", end=" ", flush=True)
        try:
            lines = _call_openrouter(prompt, model, api_key, n_per_call)
        except Exception as exc:
            print(f"ERROR: {exc}")
            continue

        batch: list[dict[str, Any]] = []
        for raw_text in lines:
            text = raw_text.strip()
            if not text:
                continue
            norm = _normalize(text)
            if norm in seen:
                continue
            seen.add(norm)
            batch.append({
                "text": text,
                "label": label,
                "lang": lang,
                "split": None,
                "source": "generated",
                "reviewed": False,
                "family_key": family_key,
                "model": model,
            })

        print(f"got {len(batch)} new")
        results.extend(batch)
        if call_idx < n_calls - 1:
            time.sleep(RATE_LIMIT_SLEEP)

    return results


# ── I/O helpers ───────────────────────────────────────────────────────────────────

def _load_existing() -> list[dict[str, Any]]:
    if not INTENTS_FILE.exists():
        return []
    examples = []
    with open(INTENTS_FILE, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                examples.append(json.loads(line))
    return examples


def _save(examples: list[dict[str, Any]]) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with open(INTENTS_FILE, "w", encoding="utf-8") as f:
        for ex in examples:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")
    print(f"Wrote {len(examples)} examples → {INTENTS_FILE}")


def _seed_entry(i: int, ex: dict[str, Any]) -> dict[str, Any]:
    return {
        "text": ex["text"],
        "label": ex["label"],
        "lang": ex["lang"],
        "split": None,
        "source": "seed",
        "reviewed": True,
        "family_key": f"seed_{ex['label']}_{ex['lang']}_{i:04d}",
        "model": None,
    }


def _print_stats(examples: list[dict[str, Any]]) -> None:
    from collections import Counter
    counts: Counter = Counter()
    for e in examples:
        counts[(e["label"], e["lang"])] += 1
    print("\nCounts per (label, lang):")
    for label in LABELS:
        row = "  " + label.ljust(16)
        for lang in LANGS:
            row += f"  {lang}:{counts[(label,lang)]:3d}"
        print(row)
    print(f"  Total: {len(examples)}")


# ── Main ──────────────────────────────────────────────────────────────────────────

def main() -> None:
    ap = argparse.ArgumentParser(description="Generate the Sidr NLU intent dataset.")
    mode = ap.add_mutually_exclusive_group(required=True)
    mode.add_argument("--seed-only", action="store_true",
                      help="Write hand-authored seeds to intents.jsonl (no network).")
    mode.add_argument("--expand", action="store_true",
                      help="Expand existing intents.jsonl with generated examples (needs API key).")
    ap.add_argument("--lang", nargs="+", choices=LANGS, default=LANGS,
                    help="Languages to process (default: all).")
    ap.add_argument("--label", nargs="+", choices=LABELS, default=LABELS,
                    help="Labels to process (default: all).")
    ap.add_argument("--calls", type=int, default=CALLS_PER_CELL,
                    help=f"API calls per (label, lang) cell (default: {CALLS_PER_CELL}).")
    ap.add_argument("--per-call", type=int, default=EXAMPLES_PER_CALL,
                    help=f"Examples requested per call (default: {EXAMPLES_PER_CALL}).")
    args = ap.parse_args()

    # ── Seed-only mode ────────────────────────────────────────────────────────────
    if args.seed_only:
        seeds = [_seed_entry(i, ex) for i, ex in enumerate(SEED_EXAMPLES)]
        _save(seeds)
        _print_stats(seeds)
        return

    # ── Expand mode ───────────────────────────────────────────────────────────────
    api_key = os.environ.get("OPENROUTER_API_KEY", "")
    if not api_key:
        print("ERROR: OPENROUTER_API_KEY not set.", file=sys.stderr)
        sys.exit(1)

    existing = _load_existing()
    if not existing:
        # Bootstrap with seeds first
        existing = [_seed_entry(i, ex) for i, ex in enumerate(SEED_EXAMPLES)]
        print(f"Bootstrapped {len(existing)} seed examples.")

    existing_norm: set[str] = {_normalize(e["text"]) for e in existing}
    new_examples: list[dict[str, Any]] = []

    target_langs = args.lang
    target_labels = args.label
    # Target count per cell (seeds + generated).  Cell already at/above target → skip.
    target_per_cell = args.calls * args.per_call + 10   # rough upper bound

    for label in target_labels:
        for lang in target_langs:
            cell_count = sum(1 for e in existing if e["label"] == label and e["lang"] == lang)
            if cell_count >= target_per_cell:
                print(f"\n=== {label} / {lang} — SKIP (already {cell_count} ≥ {target_per_cell}) ===")
                continue

            print(f"\n=== {label} / {lang} ===")
            print(f"  Existing: {cell_count}")
            generated = _generate_cell(
                label=label,
                lang=lang,
                existing_texts=existing_norm,
                api_key=api_key,
                n_calls=args.calls,
                n_per_call=args.per_call,
            )
            new_examples.extend(generated)
            for e in generated:
                existing_norm.add(_normalize(e["text"]))

            # Incremental save: persist after every cell so a timeout doesn't lose work.
            all_so_far = existing + new_examples
            _save(all_so_far)

    all_examples = existing + new_examples
    # Final save (idempotent if we already saved above)
    _save(all_examples)
    _print_stats(all_examples)

    print(f"\nNew examples generated: {len(new_examples)}")
    print("Next step: python3 tools/nlu/validate.py --write-splits")


if __name__ == "__main__":
    main()
