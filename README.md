# RaporX

GUI destekli rapor plugini. Java 21, Spigot/Paper/Purpur **1.21.x**.

## Derleme
```
mvn clean package
```
Jar: `target/RaporX-1.0.0.jar` → sunucunun `plugins/` klasörüne at.

## Komutlar
| Komut | Açıklama |
|---|---|
| `/rapor` (`/report`) | Oyuncu seçme menüsü → kategori → (hile ise tür) → sebep → onay |
| `/rapor <oyuncu>` | Direkt kategori menüsü (çevrimdışı oyuncu da olur) |
| `/rapor <oyuncu> <sebep>` | Hızlı rapor (`report.quick-category`) |
| `/raporadmin` | Admin paneli (açık/çözüldü/reddedildi filtreli) |
| `/raporadmin ac <id>` | Rapor detayı |
| `/raporadmin gecmis <oyuncu>` | Oyuncunun son 50 mesajı (çevrimdışı da) |
| `/raporadmin flag <oyuncu> <kontrol> <vl> [detay]` | Hile koruması kaydı ekler (konsoldan da) |
| `/raporadmin ayarlar` | Oyun içi ayar GUI'si (webhook, limitler, kategori düzenleyici) |
| `/raporadmin test` / `reload` | Webhook testi / yeniden yükle |

## Yetkiler
`rapor.use` (herkes), `rapor.admin`, `rapor.notify`, `rapor.punish`, `rapor.teleport`, `rapor.settings`, `rapor.flag`, `rapor.bypass`, `rapor.cooldown.bypass`

## Discord
`config.yml` → `discord.enabled: true` + `webhook-url`. Ya da oyun içinden `/raporadmin ayarlar`.
Her rapor embed olarak düşer; son 50 mesaj + hile kayıtları `.txt` eki olarak gelir. Hile raporları için ayrı webhook ve rol etiketi ayarlanabilir.

## Hile koruması bağlantısı
Anti-cheat'in flag verdiğinde şu komutu çalıştırması yeterli (konsol):
```
raporadmin flag %player% KillAura 4.5 reach=3.9
```
veya kendi pluginin içinden `RaporAPI.addFlag(uuid, ad, kontrol, vl, detay)`.
Rapor anında son 30 dk'daki flag'ler rapora eklenir ve "güçlü / zayıf / yok" kanıt olarak gösterilir.
`anticheat.auto-report.enabled: true` yaparsan eşik aşılınca sistem kendisi rapor açar.

## Ceza
`config.yml → punishments.list` içindeki komutlar konsoldan çalışır, oyuncu çevrimdışı olsa da.
Kendi ceza pluginine göre (`mute`, `tempban`, `ban`...) komutları düzenle.

## Notlar
- Menü yazıları hex (`&#RRGGBB`) ve small caps; `style.small-caps` ile kapatılabilir.
- Her menüde kapat butonu, alt menülerde geri butonu var.
- `login`, `register` gibi komutlar mesaj geçmişine **kaydedilmez** (`logging.command-blacklist`).
