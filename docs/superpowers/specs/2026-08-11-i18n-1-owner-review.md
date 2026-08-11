# I18N-1 Owner Review Package

Started by Task 7 (`feature/prayer` extraction + Diyanet terminology). Task 15 will complete this
package with the remaining consent copy. The owner reviews this document once, not once per task.

**How to read this**: every string below is either

- **Class A (locked, never translated)** — `translatable="false"` in the XML. Ships in English only,
  in every locale. Listed here purely for context (the frame's shape matters even though its words
  never change).
- **Class B (locked, translated, sign-off required)** — no `translatable="false"` attribute, so it
  ships in `values-ru` and `values-tr` like any other string, but it lives in a `strings_locked.xml`
  file specifically so a change lands in *this* document before it ships. This is the religious
  terminology (prayer names, calculation-method names, Asr madhab labels) and the consent copy that
  need your sign-off.
- **For review only, not locked** — `core/ui`'s existing prayer-domain copy. It was extracted before
  this i18n block existed and you already ruled it stays ordinary/translatable. It is **not being
  moved or re-locked**; it is included here so you can see the whole prayer-surface vocabulary in one
  place.

For each Class B row, the **Source** column says whether the Russian/Turkish text is:
- **verbatim (brief)** — the exact term specified by the Task 7 brief, copied character-for-character,
  not retyped and not my choice.
- **draft** — my translation, not given by any brief, and it has **not** been reviewed by anyone.
  Treat every "draft" cell as unverified until you sign off on it.

---

## Section 1 — `feature/prayer` locked vocabulary (Task 7)

### 1a. Class A — locked, never translated

Only one entry: the provenance frame that makes the DS-6B invariant ("a prayer surface always states
where its times came from") visible on the prayer detail screen. English-only in every locale; the two
`%s` slots are filled by the Class B method/madhab names below at render time.

| Key | English | Why locked |
|---|---|---|
| `prayer_provenance_frame` | `local calculation · %1$s · %2$s` | Provenance frame (DS-6B invariant) — the shape must never vary by locale, so it ships English-only everywhere. Extracted verbatim from `PrayerDetailScreen.kt`'s `toProvenanceText()` — note this is the screen's own lowercase "local calculation" phrasing, not the "LOCAL CALC" abbreviation used on the Home strip (`feature/launcher`, out of scope for this task). |

### 1b. Class B — prayer names

`domain.prayer.PrayerName` has six values; SUNRISE is explicitly **not** one of the five daily prayers
(spec §3) but is still a named, labelled row on the detail screen, so it needs a name too. **The
Russian and Turkish values below are verbatim from the Task 7 brief** — copied character-for-character,
not a draft, not my choice of terminology.

| Key | English | Russian | Turkish | Source | Why locked |
|---|---|---|---|---|---|
| `prayer_name_fajr` | FAJR | Фаджр | İmsak | verbatim (brief) | Prayer name — religious terminology, Diyanet (tr) / Russian Islamic-source transliteration (ru). |
| `prayer_name_sunrise` | SUNRISE | Восход | Güneş | verbatim (brief) | Named row on the detail screen; not one of the five prayers, but still religious/astronomical terminology reviewed alongside the prayer names. |
| `prayer_name_dhuhr` | DHUHR | Зухр | Öğle | verbatim (brief) | Prayer name. |
| `prayer_name_asr` | ASR | Аср | İkindi | verbatim (brief) | Prayer name. |
| `prayer_name_maghrib` | MAGHRIB | Магриб | Akşam | verbatim (brief) | Prayer name. |
| `prayer_name_isha` | ISHA | Иша | Yatsı | verbatim (brief) | Prayer name. |

English is written in the same all-caps form the code already used (the raw `PrayerName` enum constant
name, which is how these six values were rendered — via TalkBack `contentDescription` only, never
visible strip text — before this task). Russian/Turkish are given in the brief's original case; the
existing (unmodified) `core/ui` `PrayerCell` applies a locale-aware `.uppercase(Locale.getDefault())`
at render time, so the final spoken/displayed form is correctly cased per locale regardless of the
resource's authored case.

### 1c. Class B — calculation-method names

`domain.prayer.SupportedPrayerMethods.ALL` (`:domain`) is the canonical catalog and its English
`displayLabel` text is untouched — `domain` must stay Android-resource-free per this repo's hard rules,
so these 11 names are **re-hosted** here as the presentation-layer source of truth for `feature/prayer`
only (`feature/launcher`'s own copy of the Home strip, `PrayerSummaryMapper.kt`, is untouched — out of
scope for this task). English copied verbatim from `SupportedPrayerMethods.kt`. **The brief did not
specify Russian/Turkish for these — every non-English cell in this table is my draft, unreviewed.**

| Key | English (verbatim from domain) | Russian (draft) | Turkish (draft) |
|---|---|---|---|
| `prayer_method_mwl` | Muslim World League | Всемирная исламская лига | Dünya İslam Birliği |
| `prayer_method_egyptian` | Egyptian General Authority of Survey | Египетское главное управление геодезии | Mısır Genel Anket Otoritesi |
| `prayer_method_karachi` | University of Islamic Sciences, Karachi | Университет исламских наук, Карачи | İslami İlimler Üniversitesi, Karaçi |
| `prayer_method_umm_al_qura` | Umm al-Qura University, Makkah | Университет Умм аль-Кура, Мекка | Ümmü'l-Kurâ Üniversitesi, Mekke |
| `prayer_method_dubai` | Dubai (UAE) | Дубай (ОАЭ) | Dubai (BAE) |
| `prayer_method_moon_sighting_committee` | Moonsighting Committee Worldwide | Всемирный комитет по наблюдению за луной | Dünya Hilal Gözlem Komitesi |
| `prayer_method_north_america` | Islamic Society of North America (ISNA) | Исламское общество Северной Америки (ISNA) | Kuzey Amerika İslam Cemiyeti (ISNA) |
| `prayer_method_kuwait` | Kuwait | Кувейт | Kuveyt |
| `prayer_method_qatar` | Qatar | Катар | Katar |
| `prayer_method_singapore` | Majlis Ugama Islam Singapura | Majlis Ugama Islam Singapura *(kept — official Malay institution name, not translated)* | Majlis Ugama Islam Singapura *(kept — same reasoning)* |
| `prayer_method_turkey` | Diyanet İşleri Başkanlığı, Turkey | Diyanet İşleri Başkanlığı, Турция *(institution name kept, country translated — same pattern as the English source)* | Diyanet İşleri Başkanlığı, Türkiye |

### 1d. Class B — Asr madhab labels

`domain.prayer.Madhab` has two values. English copied verbatim from the existing (duplicated,
one-per-file) `madhabLabel()` functions in `PrayerSettingsScreen.kt`/`PrayerDetailScreen.kt`. **Draft,
unreviewed** — the brief did not specify Russian/Turkish for these.

| Key | English (verbatim) | Russian (draft) | Turkish (draft) |
|---|---|---|---|
| `prayer_madhab_standard` | Standard (Shafi'i / Maliki / Hanbali) | Стандартный (шафиитский / маликитский / ханбалитский) | Standart (Şafii / Maliki / Hanbeli) |
| `prayer_madhab_hanafi` | Hanafi | Ханафитский | Hanefi |

---

## Section 2 — `core/ui` prayer-domain copy (extracted before this task; for review only)

These 15 keys live in `core/ui/src/main/res/values{,-ru,-tr}/strings.xml` — **ordinary translatable
strings, not locked**, extracted in an earlier task before this i18n block's Task 7 existed. The owner
has already ruled these stay translatable (not moved, not re-locked). Included here only so the full
prayer-surface vocabulary is visible in one review pass, per this task's brief.

| Key | English | Russian | Turkish |
|---|---|---|---|
| `ui_prayer_status_verified` | VERIFIED | ПРОВЕРЕНО | DOĞRULANDI |
| `ui_prayer_status_cached` | CACHED | ИЗ КЭША | ÖNBELLEKTEN |
| `ui_prayer_status_stale` | STALE | УСТАРЕЛО | ESKİMİŞ |
| `ui_prayer_status_manual_location` | MANUAL LOCATION | МЕСТО ВРУЧНУЮ | KONUM ELLE |
| `ui_prayer_status_location_unavailable` | LOCATION UNAVAILABLE | МЕСТО НЕДОСТУПНО | KONUM YOK |
| `ui_prayer_status_method_required` | METHOD REQUIRED | НУЖЕН МЕТОД | YÖNTEM GEREKLİ |
| `ui_prayer_status_authority_unavailable` | AUTHORITY UNAVAILABLE | ИСТОЧНИК НЕДОСТУПЕН | KAYNAK YOK |
| `ui_prayer_status_timezone_conflict` | TZ CONFLICT | КОНФЛИКТ ЧАСОВОГО ПОЯСА | SAAT DİLİMİ ÇAKIŞMASI |
| `ui_prayer_status_calculation_failed` | CALCULATION FAILED | ОШИБКА РАСЧЁТА | HESAPLAMA BAŞARISIZ |
| `ui_prayer_status_no_data` | NO DATA | НЕТ ДАННЫХ | VERİ YOK |
| `ui_prayer_status_updating` | UPDATING | ОБНОВЛЕНИЕ | GÜNCELLENİYOR |
| `ui_prayer_summary_content_description` | prayer times | время намаза | namaz vakitleri |
| `ui_prayer_summary_next_prayer` | next %1$s %2$s | следующий %1$s %2$s | sıradaki %1$s %2$s |
| `ui_prayer_next_cell_content_description` | Next %1$s | Следующий %1$s | Sıradaki %1$s |
| `ui_prayer_details_action_label` | Prayer details | Подробности о намазе | Namaz ayrıntıları |

---

## Section 3 — `feature/permission_education` consent copy (Class B, landed before Task 7)

Five Class B keys, one full permission-rationale sentence per `PermissionFeature` (the "why" a
permission is being asked for). These are a **different review concern from Section 1** — consent/
privacy copy, not Diyanet/Islamic terminology — kept in their own section deliberately. Extracted
verbatim from the pre-I18N-1 Kotlin source (`PermissionRationale.kt`'s `rationaleFor()`); Russian and
Turkish are **drafts, unreviewed** (no brief gave exact wording for these).

| Key | English | Russian (draft) | Turkish (draft) |
|---|---|---|---|
| `perm_rationale_wallpaper` | Sidr can open the wallpaper picker so you can personalise your home screen. This is optional — the launcher works fully without it, and you can change your mind any time. | Sidr может открыть выбор обоев, чтобы вы могли персонализировать главный экран. Это необязательно — лаунчер полностью работает и без этого, и вы можете передумать в любой момент. | Sidr, ana ekranınızı kişiselleştirebilmeniz için duvar kağıdı seçiciyi açabilir. Bu isteğe bağlıdır — başlatıcı onsuz da tamamen çalışır ve istediğiniz zaman fikrinizi değiştirebilirsiniz. |
| `perm_rationale_voice_input` | Voice input lets you speak commands instead of typing. The launcher only listens while you tap the mic, never records in the background, and prefers on-device recognition. It's optional — typing always works without it. | Голосовой ввод позволяет произносить команды вместо ввода текста. Лаунчер слушает только пока вы удерживаете значок микрофона, никогда не записывает в фоновом режиме и предпочитает распознавание на устройстве. Это необязательно — ввод текста всегда работает и без этого. | Sesli giriş, komutları yazmak yerine söylemenizi sağlar. Başlatıcı yalnızca mikrofona dokunduğunuzda dinler, hiçbir zaman arka planda kayıt yapmaz ve cihaz üzerinde tanımayı tercih eder. İsteğe bağlıdır — yazmak onsuz da her zaman çalışır. |
| `perm_rationale_calendar` | Reading your calendar lets Sidr suggest the right thing at the right time, like nudging you toward an upcoming event. Sidr only checks whether something is coming up soon — it never stores event titles, times, or details. It's optional — suggestions work fine without it, and you can change your mind any time. | Чтение календаря позволяет Sidr предлагать нужное в нужный момент, например напоминать о предстоящем событии. Sidr только проверяет, не приближается ли что-то — он никогда не сохраняет названия событий, время или подробности. Это необязательно — подсказки прекрасно работают и без этого, и вы можете передумать в любой момент. | Takviminizi okumak, Sidr'in yaklaşan bir etkinliği hatırlatmak gibi doğru zamanda doğru şeyi önermesini sağlar. Sidr yalnızca yakında bir şey olup olmadığını kontrol eder — etkinlik başlıklarını, saatlerini veya ayrıntılarını asla saklamaz. İsteğe bağlıdır — öneriler onsuz da sorunsuz çalışır ve istediğiniz zaman fikrinizi değiştirebilirsiniz. |
| `perm_rationale_location` | Using your location lets Sidr surface nearby-relevant actions, like suggesting a maps app when you're out and about. Sidr only checks whether a recent location fix exists — it never stores or sends your coordinates. It's optional — suggestions work fine without it, and you can change your mind any time. | Использование вашего местоположения позволяет Sidr предлагать актуальные поблизости действия, например предлагать приложение карт, когда вы куда-то идёте. Sidr только проверяет, есть ли недавние данные о местоположении — он никогда не сохраняет и не отправляет ваши координаты. Это необязательно — подсказки прекрасно работают и без этого, и вы можете передумать в любой момент. | Konumunuzun kullanılması, dışarıdayken bir harita uygulaması önermek gibi yakınla ilgili eylemleri Sidr'in göstermesini sağlar. Sidr yalnızca yakın zamanda alınmış bir konum bilgisi olup olmadığını kontrol eder — koordinatlarınızı asla saklamaz veya göndermez. İsteğe bağlıdır — öneriler onsuz da sorunsuz çalışır ve istediğiniz zaman fikrinizi değiştirebilirsiniz. |
| `perm_rationale_prayer_location` | Using your device location lets Sidr compute accurate prayer times for where you are, rounded to about a kilometre before it's ever stored. Your coordinates stay on this device — they are never sent anywhere or logged. It's entirely optional: picking a city from the list works without this permission, and you can change your mind any time. | Использование геолокации устройства позволяет Sidr точно рассчитывать время намаза для вашего местоположения, округлённого примерно до километра ещё до сохранения. Ваши координаты остаются на этом устройстве — они никогда никуда не отправляются и не записываются в журнал. Это полностью необязательно: выбор города из списка работает без этого разрешения, и вы можете передумать в любой момент. | Cihaz konumunuzun kullanılması, Sidr'in bulunduğunuz yer için doğru namaz vakitlerini hesaplamasını sağlar; bu konum kaydedilmeden önce yaklaşık bir kilometreye yuvarlanır. Koordinatlarınız bu cihazda kalır — hiçbir yere gönderilmez veya kaydedilmez. Bu tamamen isteğe bağlıdır: listeden bir şehir seçmek bu izin olmadan da çalışır ve istediğiniz zaman fikrinizi değiştirebilirsiniz. |

---

## Outstanding for Task 15

- Any additional consent copy from later tasks (Task 15 scope).
- Owner sign-off on every row marked **draft** above (Sections 1c, 1d, 3) — none of that text has been
  reviewed by anyone; only the prayer-name rows in Section 1b are verbatim from an approved brief.
