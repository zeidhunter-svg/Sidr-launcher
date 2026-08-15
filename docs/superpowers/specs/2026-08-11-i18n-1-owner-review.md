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

## Section 4 — `feature/settings` consent & data-handling copy (Class B, Task 8)

Six Class B keys — the cloud-disclosure toggle description, both destructive-gate consequence sentences,
the standalone "stored locally" evidence line, and the two empty-state messages that each embed that same
sentence — found missing from the locked-file barrier during Task 8 review and moved into
`feature/settings/src/main/res/values{,-ru,-tr}/strings_locked.xml` in the fix round. Extracted verbatim
from the pre-fix-round `strings.xml` (no copy change); Russian and Turkish are **drafts, unreviewed** (the
same drafts already shipped in Task 8's `strings.xml`, just relocated — no wording changed by the move).

| Key | English | Russian (draft) | Turkish (draft) | Why locked |
|---|---|---|---|---|
| `settings_smart_routing_description` | Use your AI provider to understand natural-language commands. Suggested actions always ask before running. | Использовать вашего ИИ-провайдера для понимания команд на естественном языке. Предложенные действия всегда запрашивают подтверждение перед выполнением. | Doğal dil komutlarını anlamak için yapay zeka sağlayıcınızı kullanın. Önerilen eylemler çalıştırılmadan önce her zaman onay ister. | Cloud-disclosure copy (spec §7.2) — the consent point for routing typed commands to a cloud LLM; second sentence states the project's R4 safety invariant. |
| `settings_forget_alias_consequence` | "%1$s" will no longer open %2$s.\nYou can add it again from Settings. | "%1$s" больше не будет открывать %2$s.\nВы можете добавить его снова в настройках. | "%1$s" artık %2$s uygulamasını açmayacak.\nAyarlardan tekrar ekleyebilirsiniz. | Destructive-gate consequence sentence (spec §7.2) — the `consequence` param of `SidrForgetGate` → `SidrActionGate(type = Destructive)` for an alias. |
| `settings_forget_learned_choice_consequence` | "%1$s" will no longer prefer %2$s.\nThe next ambiguous request will ask you to choose again. | "%1$s" больше не будет предпочитать %2$s.\nСледующий неоднозначный запрос снова спросит ваш выбор. | "%1$s" artık %2$s uygulamasını tercih etmeyecek.\nBir sonraki belirsiz istek tekrar seçim yapmanızı isteyecek. | Destructive-gate consequence sentence (spec §7.2) — the `consequence` param of `SidrForgetGate` → `SidrActionGate(type = Destructive)` for a learned choice. |
| `settings_stored_locally` | Stored only on this device. | Хранится только на этом устройстве. | Yalnızca bu cihazda saklanır. | Same species as spec §7.2's named "Nothing was saved" — a data-handling claim — and also the `evidence` line shown on both destructive gates above. |
| `settings_aliases_empty_message` | No aliases yet\nAliases appear after you add them here.\nStored only on this device. | Пока нет псевдонимов\nПсевдонимы появятся здесь после того, как вы их добавите.\nХранится только на этом устройстве. | Henüz takma ad yok\nTakma adlar burada eklendikten sonra görünür.\nYalnızca bu cihazda saklanır. | Embeds "Stored only on this device." verbatim as its last line — a second, otherwise-unreviewed copy of the same privacy claim as `settings_stored_locally`. |
| `settings_learned_choices_empty_message` | No learned choices yet\nPreferences appear only after confirmed choices.\nStored only on this device. | Изученных выборов пока нет\nПредпочтения появляются только после подтверждённых выборов.\nХранится только на этом устройстве. | Henüz öğrenilmiş seçim yok\nTercihler yalnızca onaylanan seçimlerden sonra görünür.\nYalnızca bu cihazda saklanır. | Embeds "Stored only on this device." verbatim as its last line — a third, otherwise-unreviewed copy of the same privacy claim as `settings_stored_locally`. |

**Note — one sentence, three keys:** "Stored only on this device." appears in **three** places:
`settings_stored_locally` (standalone) and, embedded as the final line, both `settings_aliases_empty_message`
and `settings_learned_choices_empty_message`. Whatever wording the owner signs off on for this sentence
must be applied identically in all three keys/locations — do not approve one copy and leave the other two
stale.

---

## Section 5 — `feature/assistant` consent & data-handling copy (Task 10)

The Assistant is the **only** surface in this app that sends anything off the device, so its disclosure
copy is the most load-bearing consent text in the product. Seven Class B keys live in
`feature/assistant/src/main/res/values{,-ru,-tr}/strings_locked.xml`; nine Class A keys (the neutral
host sentinel, the provenance vocabulary, and the three field placeholders) ship English-only and are
listed first for context. Extracted verbatim from the pre-I18N-1 Kotlin literals in
`AssistantPresentation.kt` / `AssistantScreen.kt` — no copy change. Russian and Turkish are **drafts,
unreviewed**.

### 5a. Class A — locked, never translated

Spec §7.1 machine vocabulary and data. `translatable="false"`, absent from `values-ru`/`values-tr`.

| Key | English | Why locked |
|---|---|---|
| `assistant_provider_unknown_host` | (unknown host) | Neutral sentinel rendered when a stored `baseUrl` has no parseable host. `saveProvider` validates only the `https://` prefix, so this is the guard that keeps a malformed URL's scheme off the screen and out of TalkBack — a sentinel, not copy. The pure `providerHost()` returns `null` and this resource *is* the fallback, so no second copy can drift from it. |
| `assistant_provenance_cloud` | cloud | Provenance token rendered through `SidrProvenanceLine`, which upper-cases with `Locale.ROOT` precisely because these are locked vocabulary, not copy (a Turkish default locale would otherwise render `KEY İN KEYSTORE`). |
| `assistant_provenance_local_only` | local only | Provenance token — the honest source for every not-yet-configured state. |
| `assistant_provenance_no_provider` | no provider configured | Provenance token. |
| `assistant_provenance_key_in_keystore` | key in keystore | Provenance token — states key *presence* only; the key itself never reaches the UI. |
| `assistant_provenance_no_key_set` | no key set | Provenance token. |
| `assistant_placeholder_base_url` | https://openrouter.ai/api/v1 | Provider-form field placeholder — example data (a URL), not copy. |
| `assistant_placeholder_model` | mistralai/mistral-7b-instruct | Provider-form field placeholder — example data (a model id), not copy. Same species as the two placeholders above. |
| `assistant_placeholder_api_key` | sk-… | Provider-form field placeholder — example data (a key prefix), not copy. |

### 5b. Class B — the cloud disclosure and the data-handling claims

| Key | English | Russian (draft) | Turkish (draft) | Why locked |
|---|---|---|---|---|
| `assistant_cloud_disclosure_title` | Replies come from your provider | Ответы приходят от вашего провайдера | Yanıtlar sağlayıcınızdan gelir | Heading of spec §7.2's named cloud disclosure — shown as the resting state before the first Send, and above the provider form. |
| `assistant_cloud_disclosure_body` | When you tap Send, the message you typed is sent to the AI provider you configured. Nothing else goes with it — no apps, no history, no location, no memory. Your API key stays in this device's Keystore and is never shown or sent anywhere else. | Когда вы нажимаете «Отправить сообщение», введённое вами сообщение отправляется настроенному вами ИИ-провайдеру. Вместе с ним не отправляется ничего другого — ни приложения, ни история, ни местоположение, ни память. Ваш ключ API остаётся в хранилище ключей (Keystore) этого устройства и никогда не показывается и не отправляется куда-либо ещё. | Mesaj gönder'e dokunduğunuzda, yazdığınız mesaj yapılandırdığınız yapay zeka sağlayıcısına gönderilir. Bununla birlikte başka hiçbir şey gönderilmez — uygulamalar, geçmiş, konum ve bellek gönderilmez. API anahtarınız bu cihazın Keystore'unda kalır; asla gösterilmez veya başka bir yere gönderilmez. | **The** sentence that tells the user their typed message leaves the device, what does *not* go with it, and where the key stays. Spec §7.2 names the cloud disclosure verbatim. The ru/tr drafts say exactly what the English says and claim nothing more; "Send"/"Mesaj gönder"/«Отправить сообщение» name the composer's own button label as it ships in each locale (`ui_assistant_send_ready`). |
| `assistant_no_provider_title` | No AI provider configured | ИИ-провайдер не настроен | Yapay zeka sağlayıcısı yapılandırılmadı | Heading of the same disclosure in its not-yet-configured state. |
| `assistant_no_provider_body` | The assistant needs a provider you configure and pay for. Until then nothing is sent anywhere, and the rest of the launcher keeps working offline. | Ассистенту нужен провайдер, которого вы настраиваете и оплачиваете сами. До этого никуда ничего не отправляется, а остальная часть лаунчера продолжает работать офлайн. | Asistanın, sizin yapılandırdığınız ve ücretini ödediğiniz bir sağlayıcıya ihtiyacı var. O zamana kadar hiçbir yere hiçbir şey gönderilmez ve başlatıcının geri kalanı çevrimdışı çalışmaya devam eder. | Carries the "nothing is sent anywhere" promise for the unconfigured state — a data-handling claim of the same species as the disclosure itself. |
| `assistant_error_next_retry` | Nothing was saved. Send the same message again when you are ready. | Ничего не было сохранено. Отправьте то же сообщение снова, когда будете готовы. | Hiçbir şey kaydedilmedi. Hazır olduğunuzda aynı mesajı tekrar gönderin. | Begins with "Nothing was saved." — the phrase spec §7.2 names verbatim. |
| `assistant_error_next_reword` | Nothing was saved. Rewording the message usually helps. | Ничего не было сохранено. Обычно помогает переформулировать сообщение. | Hiçbir şey kaydedilmedi. Mesajı yeniden ifade etmek genellikle yardımcı olur. | The second "Nothing was saved." sentence — same claim, different next step. |
| `assistant_key_set_note` | Key set — stored in this device's Keystore. Enter a new one to replace it. | Ключ задан — хранится в хранилище ключей (Keystore) этого устройства. Введите новый, чтобы заменить его. | Anahtar ayarlandı — bu cihazın Keystore'unda saklanıyor. Değiştirmek için yenisini girin. | A data-handling claim about where the user's secret lives — the same species as Task 8's `settings_stored_locally` (Section 4). |

**Note — the "Nothing was saved." sentence, two keys:** exactly as with Section 4's "Stored only on this
device.", this phrase appears in **two** keys (`assistant_error_next_retry`, `assistant_error_next_reword`).
Whatever wording is signed off must be applied identically to both; do not approve one and leave the other
stale.

**Note — third `next` sentence, deliberately not locked:** the credential-error next step
("Open provider settings and check the base URL, model, and API key.", `assistant_error_next_provider`)
makes no data-handling claim and does not carry the "Nothing was saved." phrase, so it ships as ordinary
translatable copy in `strings.xml`.

---

## Section 6 — `feature/launcher` locked vocabulary (Task 13)

Two review concerns land in the same file (`feature/launcher/src/main/res/values{,-ru,-tr}/strings_locked.xml`):
the Home strip's copy of the prayer method/madhab vocabulary (a **second copy** of Section 1's terminology —
see the divergence note below) and the Shahada, the Islamic creed text rendered on Home
(`LauncherScreen.kt`'s `HomeAnchorSlot`, the DS-4 "sacred anchor"). Extracted verbatim from the pre-I18N-1
Kotlin literals; Russian and Turkish are **drafts, unreviewed**, except the method-name rows, which reuse
Section 1c's existing Russian/Turkish draft verbatim (same terminology, same review needed once).

### 6a. Class A — locked, never translated

| Key | English | Why locked |
|---|---|---|
| `launcher_prayer_provenance_frame` | `LOCAL CALC · %1$s · %2$s` | Provenance frame (DS-6B invariant), Home strip's own copy — note the **ALL-CAPS "LOCAL CALC"** abbreviation, distinct from Section 1a's lowercase `local calculation · %1$s · %2$s` on the prayer detail screen. Both are pre-existing, correct copy for their own screen; this task did not reconcile them (see the "Outstanding" note below). |
| `launcher_drawer_groups_provenance` | `ON-DEVICE · OFFLINE` | Terminal state/provenance token on the App Drawer's "Groups" design-preview grid, same species as spec §7.1's enumerated examples (`LOCAL ONLY`, `LOCAL CALC`) but not itself one of the enumerated strings. **Judgment call, not directed by the task brief** — locked here on the same reasoning as the enumerated tokens; owner may choose to unlock it instead. |

### 6b. Class B — calculation-method names (second copy of Section 1c)

Re-hosted here for the same reason as Section 1c: `domain` stays Android-resource-free, so
`feature/launcher`'s `PrayerSummaryMapper.kt` (Home strip) needs its own presentation-layer copy of
`SupportedPrayerMethods.ALL`'s English `displayLabel`s — byte-identical to Section 1c's English, since both
source from the same domain field. **Russian/Turkish here are the exact same draft text as Section 1c**,
reused verbatim for terminology consistency across the two screens — sign off once, both screens match.

| Key | English (verbatim from domain) | Russian (draft, = Section 1c) | Turkish (draft, = Section 1c) |
|---|---|---|---|
| `launcher_prayer_method_mwl` | Muslim World League | Всемирная исламская лига | Dünya İslam Birliği |
| `launcher_prayer_method_egyptian` | Egyptian General Authority of Survey | Египетское главное управление геодезии | Mısır Genel Anket Otoritesi |
| `launcher_prayer_method_karachi` | University of Islamic Sciences, Karachi | Университет исламских наук, Карачи | İslami İlimler Üniversitesi, Karaçi |
| `launcher_prayer_method_umm_al_qura` | Umm al-Qura University, Makkah | Университет Умм аль-Кура, Мекка | Ümmü'l-Kurâ Üniversitesi, Mekke |
| `launcher_prayer_method_dubai` | Dubai (UAE) | Дубай (ОАЭ) | Dubai (BAE) |
| `launcher_prayer_method_moon_sighting_committee` | Moonsighting Committee Worldwide | Всемирный комитет по наблюдению за луной | Dünya Hilal Gözlem Komitesi |
| `launcher_prayer_method_north_america` | Islamic Society of North America (ISNA) | Исламское общество Северной Америки (ISNA) | Kuzey Amerika İslam Cemiyeti (ISNA) |
| `launcher_prayer_method_kuwait` | Kuwait | Кувейт | Kuveyt |
| `launcher_prayer_method_qatar` | Qatar | Катар | Katar |
| `launcher_prayer_method_singapore` | Majlis Ugama Islam Singapura | Majlis Ugama Islam Singapura *(kept)* | Majlis Ugama Islam Singapura *(kept)* |
| `launcher_prayer_method_turkey` | Diyanet İşleri Başkanlığı, Turkey | Diyanet İşleri Başkanlığı, Турция | Diyanet İşleri Başkanlığı, Türkiye |

### 6c. Class B — Asr madhab labels (diverges from Section 1d — see note)

| Key | English (verbatim) | Russian (draft) | Turkish (draft) |
|---|---|---|---|
| `launcher_prayer_madhab_standard` | Standard | Стандартный | Standart |
| `launcher_prayer_madhab_hanafi` | Hanafi | Ханафитский | Hanefi |

**Divergence, not fixed here (owner's call):** Section 1d's `prayer_madhab_standard` (the prayer *detail*
screen, `feature/prayer`) reads **"Standard (Shafi'i / Maliki / Hanbali)"** — the qualified form. This
task's `launcher_prayer_madhab_standard` (the Home *strip*, `feature/launcher`) reads plain **"Standard"** —
the pre-existing English literal in `PrayerSummaryMapper.kt`, kept byte-identical per this block's "no
copy change while extracting" rule (spec §12). The two screens have shipped different English for the same
concept since before this i18n block existed; this task preserves that fact rather than silently
reconciling it. If the owner wants them to match, that is a copy decision for a follow-up, not an I18N-1
extraction detail.

### 6d. Class B — the Shahada (new religious content, not previously reviewed)

| Key | English (verbatim) | Russian (draft) | Turkish (draft) |
|---|---|---|---|
| `launcher_shahada_line1` | There is no deity except Allah | Нет бога, кроме Аллаха | Allah'tan başka ilah yoktur |
| `launcher_shahada_line2` | Muhammad is the messenger of Allah | Мухаммад — посланник Аллаха | Muhammed Allah'ın elçisidir |

Rendered on Home (`LauncherScreen.kt`'s `HomeAnchorSlot`, DS-4's "sacred anchor" — `SidrTextRole.SACRED`).
**Not named by the task brief at all** — found by inspection while enumerating every literal in
`LauncherScreen.kt` (spec §7.2's religious-terminology bar clearly applies to Islamic creed text, arguably
more sensitively than prayer-method names). Locked as Class B on the same reasoning as Sections 1b/6b/6c.
The Russian/Turkish drafts are standard renderings already in wide use in Russian-/Turkish-language
Islamic sources, not an original translation — but they are still unreviewed by the owner and must not
ship translated (only English) until signed off.

---

## Section 7 — `app` locked vocabulary (Task 15)

One entry, found while closing the barrier-1 heuristic gap in `SidrTabScaffold.kt`
(`app/src/main/res/values/strings_locked.xml`).

### 7a. Class A — locked, never translated

| Key | English | Why locked |
|---|---|---|
| `app_wordmark_sidr_os` | SIDR OS | The `SIDR`/`SIDR OS` brand wordmarks are locked as **data, not copy** by spec §7.1 — the same reasoning as `app_name` (already in this file, pre-dating I18N-1). Rendered in `SidrAppFooter`'s bottom-right corner; also the hidden 7-tap dev-mode arm target. `translatable="false"`; absent from `values-ru`/`values-tr` by construction, and `LocaleCompletenessGuardTest` fails the build if it ever appears in either. |

(The other four literals barrier 1 found in the same file — the tab preview accessibility suffix,
the terminal/settings footer icon descriptions, and the chrome-handle reveal label — are ordinary UI
copy, extracted as translatable `app_*` strings with ru/tr drafts. They carry no data-handling claim or
religious/consent content, so they do not meet the Class A/B bar and are not listed in this review
package.)

---

## Outstanding for Task 15

- Any additional consent copy from later tasks (Task 15 scope). **Resolved:** Task 14 (per-app language
  via `AppCompatDelegate` + `locales_config`) added no user-facing copy at all — it is a settings-list
  entry point wired to the OS language picker, with no new string of its own. Task 15's own find is
  Section 7 above (one Class A key, `app_wordmark_sidr_os`); no other later task added locked copy.
- Owner sign-off on every row marked **draft** above (Sections 1c, 1d, 3, 4, 5b) — none of that text has been
  reviewed by anyone; only the prayer-name rows in Section 1b are verbatim from an approved brief.
- `feature/assistant`'s `assistant_save_error_key_failed` ("Failed to save API key", in
  `feature/assistant/src/main/res/values/strings.xml`) ships as **ordinary translatable copy, not
  locked** — ruled at Task 10 review: it is an error message about a failed write, not a disclosure or a
  data-handling claim, so it does not meet the spec §7.2 bar. Recorded here anyway because it is a
  statement about whether the user's *secret* was persisted, and the owner may prefer it locked; if so
  it moves to `strings_locked.xml` as Class B with no wording change. (The Keystore *disclosure* copy —
  `assistant_key_set_note`, "stored in this device's Keystore" — is already locked, Section 5b.)
- `feature/prayer`'s `prayer_settings_privacy_body` (in `feature/prayer/src/main/res/values/strings.xml`,
  "Prayer times are computed on this device… never sent anywhere and never logged") is the same species of
  data-handling claim as `settings_stored_locally` (Section 4) but shipped in Task 7 as ordinary,
  unlocked copy rather than through a `strings_locked.xml` barrier. The owner should review that sentence
  alongside Section 4; Task 15 reconciles its file placement (this note does not move or edit any
  `feature/prayer` file).
- **Task 13 judgment calls, not directed by the brief** (see Section 6): (1) `launcher_route_app` /
  `_web` / `_site` / `_ask` (`SidrRouteChip` labels `APP`/`WEB`/`SITE`/`ASK` on Home's route lane) were
  extracted as **ordinary translatable** strings, not locked — they are plain English words meant to be
  read/understood rather than opaque state codes, and spec §7.1's enumerated list does not name them.
  The owner may prefer them locked (English-only) instead, matching the design system's other short
  chip vocabulary. (2) `launcher_drawer_groups_provenance` ("ON-DEVICE · OFFLINE") was locked as Class A
  even though not itself enumerated in spec §7.1 — see Section 6a. (3) The two prayer-provenance frames
  (Section 1a `local calculation · …` vs Section 6a `LOCAL CALC · …`) and the two madhab-standard labels
  (Section 1d vs Section 6c) remain **unreconciled duplicate English copy** across `feature/prayer` and
  `feature/launcher` — flagged, not fixed, per instruction.

---

## The release gate now exists — clearing it IS your sign-off

Task 15 added a real build-time gate, `:app:checkOwnerReviewedLocaleStrings` (`app/build.gradle.kts`),
that `assembleRelease` depends on. **It fails a release build today**, by name, for all 10 translated
`strings_locked.xml` files in the repo (`feature/assistant`, `feature/launcher`, `feature/prayer`,
`feature/permission_education`, `feature/settings`, each `values-ru` + `values-tr`) — every one of them,
because every one of them holds ru/tr text nobody but the extracting agent has read.

**What clears it, per file:** once you have personally read and approved that file's Russian and Turkish
text against this document, add the token `OWNER-REVIEWED` to that file's header XML comment (any form,
e.g. `<!-- OWNER-REVIEWED 2026-08-16 -->`). There is no separate "sign-off" step or form — **adding that
token to a file's header is the act of signing off on it**, and it is the only thing that lets a `ru`/`tr`
release build ship that file's translated copy. Nothing ships translated until you do this; there is no
default-open path.

**Read the Shahada draft yourself before anything else.** Section 6d above — the Russian/Turkish
rendering of the Shahada, the Islamic creed ("There is no deity except Allah" / "Muhammad is the
messenger of Allah") — is **the single most sensitive item in this entire block**. It is rendered on Home
as the DS-4 "sacred anchor," it was found by inspection rather than named in any brief, and its ru/tr text
is an unreviewed agent draft like everything else here, even though it draws on standard renderings
already in wide use in Russian-/Turkish-language Islamic sources. Please read Section 6d personally,
carefully, and first — before signing off on any other file — and only then decide whether
`feature/launcher/src/main/res/values-{ru,tr}/strings_locked.xml` earns the `OWNER-REVIEWED` token.
