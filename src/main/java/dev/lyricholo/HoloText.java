package dev.lyricholo;

/** Small built-in UI dictionary; content data such as song titles is never translated. */
final class HoloText {
    enum Language { ENGLISH, RUSSIAN, BULGARIAN, GERMAN, DUTCH }

    static String t(String key) {
        Language language;
        try { language=Language.valueOf(LyricHoloClient.CONFIG.language); }
        catch (RuntimeException ignored) { language=Language.ENGLISH; }
        return switch(language) {
            case RUSSIAN -> ru(key);
            case BULGARIAN -> bg(key);
            case GERMAN -> de(key);
            case DUTCH -> nl(key);
            default -> key;
        };
    }

    private static String ru(String k) { return switch(k) {
        case "CONTROL DECK" -> "ПАНЕЛЬ УПРАВЛЕНИЯ"; case "AUTOMATIC MEDIA SESSION" -> "АВТОМАТИЧЕСКИЙ МЕДИАСЕАНС";
        case "DISPLAY" -> "ДИСТАНЦИЯ"; case "SYNC" -> "СИНХР."; case "KEEP AHEAD" -> "ОПЕРЕЖЕНИЕ";
        case "SCREEN STICKY" -> "ПРИВЯЗКА К ЭКРАНУ"; case "LYRICS" -> "ТЕКСТ ПЕСНИ"; case "CORNER LYRIC" -> "ТЕКСТ В УГЛУ";
        case "WORD SYNC" -> "СИНХР. СЛОВ"; case "WORD SYNC MODE" -> "РЕЖИМ СИНХР. СЛОВ"; case "HIGHLIGHT" -> "ПОДСВЕТКА"; case "POP UP" -> "ВСПЛЫВАЮЩИЙ ТЕКСТ"; case "POP" -> "POP"; case "STACK" -> "НАКОПЛЕНИЕ"; case "CHOOSE SAVED PROFILE" -> "ВЫБРАТЬ ПРОФИЛЬ"; case "3D ANIM" -> "3D-АНИМАЦИЯ"; case "3D PLATE" -> "3D-ПОДЛОЖКА";
        case "HUD CARD" -> "ПАНЕЛЬ HUD"; case "FACE CAMERA" -> "К КАМЕРЕ"; case "TEXT / THEME" -> "ТЕКСТ / ТЕМА";
        case "LRCLIB SOURCE" -> "ИСТОЧНИК LRCLIB"; case "YT MUSIC SOURCE" -> "ИСТОЧНИК YT MUSIC";
        case "YT MUSIC API" -> "YT MUSIC API";
        case "LANGUAGE" -> "ЯЗЫК"; case "CLOSE / ESC" -> "ЗАКРЫТЬ / ESC"; case "ON" -> "ВКЛ"; case "OFF" -> "ВЫКЛ";
        case "TEXT / HOLOGRAM SETTINGS" -> "ТЕКСТ / НАСТРОЙКИ ГОЛОГРАММЫ"; case "FONT • EFFECTS • SKY • WORD WINDOW" -> "ШРИФТ • ЭФФЕКТЫ • НЕБО • СЛОВА";
        case "SKY TEXT" -> "ТЕКСТ В НЕБЕ"; case "THEME" -> "ТЕМА"; case "FONT" -> "ШРИФТ"; case "TEXT SHADOW" -> "ТЕНЬ ТЕКСТА";
        case "TEXT STROKE" -> "КОНТУР ТЕКСТА"; case "3D BACKPLATE" -> "3D-ПОДЛОЖКА"; case "HUD BACKPLATE" -> "ПОДЛОЖКА HUD";
        case "TEXT SIZE" -> "РАЗМЕР ТЕКСТА"; case "APPLY EXACT PIXELS" -> "ПРИМЕНИТЬ РАЗМЕР";
        case "BACKPLATE OPACITY" -> "ПРОЗРАЧНОСТЬ ПОДЛОЖКИ"; case "WORDS PER DISPLAY" -> "СЛОВ ЗА РАЗ";
        case "ALL" -> "ВСЕ";
        case "TEXT COLOR / HEX" -> "ЦВЕТ ТЕКСТА / HEX"; case "BACK TO CONTROL DECK" -> "НАЗАД К ПАНЕЛИ";
        case "TEXT COLOR" -> "ЦВЕТ ТЕКСТА"; case "SWATCHES / HEX VALUE" -> "ПАЛИТРА / HEX-КОД"; case "HEX COLOR" -> "HEX-ЦВЕТ";
        case "APPLY COLOR" -> "ПРИМЕНИТЬ ЦВЕТ"; case "BACK TO CONTROLS" -> "НАЗАД К НАСТРОЙКАМ";
        case "SKY HEIGHT" -> "ВЫСОТА В НЕБЕ"; case "FIXED SKY Y OFFSET" -> "СМЕЩЕНИЕ Y В НЕБЕ"; case "SKY Y ABOVE TORSO" -> "Y В НЕБЕ НАД ТУЛОВИЩЕМ"; case "DISTANCE" -> "ДИСТАНЦИЯ";
        case "PREVIOUS TEXT HOLD" -> "ВРЕМЯ СТАРОГО ТЕКСТА";
        case "NO ACTIVE MEDIA SESSION" -> "НЕТ АКТИВНОГО МЕДИАСЕАНСА"; case "PLAY AUDIO IN SPOTIFY, YOUTUBE OR A BROWSER" -> "ВКЛЮЧИТЕ МУЗЫКУ В SPOTIFY, YOUTUBE ИЛИ БРАУЗЕРЕ";
        case "NOW PLAYING / LIVE LYRICS" -> "СЕЙЧАС ИГРАЕТ / ТЕКСТ ПЕСНИ";
        case "Playing  •  lyrics synced" -> "ИГРАЕТ • ТЕКСТ СИНХРОНИЗИРОВАН"; case "Paused  •  lyrics hold position" -> "ПАУЗА • ТЕКСТ ОСТАНОВЛЕН";
        case "Finding synced lyrics…" -> "ПОИСК СИНХРОННОГО ТЕКСТА…"; case "No synced lyrics found" -> "СИНХРОННЫЙ ТЕКСТ НЕ НАЙДЕН";
        case "Synced lyrics ready" -> "ТЕКСТ СИНХРОНИЗИРОВАН"; case "Lyrics hidden" -> "ТЕКСТ СКРЫТ";
        case "Waiting for a song to play" -> "ОЖИДАНИЕ ВОСПРОИЗВЕДЕНИЯ"; case "Waiting for Spotify, Apple Music or browser audio" -> "ОЖИДАНИЕ МУЗЫКИ SPOTIFY, APPLE MUSIC ИЛИ БРАУЗЕРА";
        default -> k;
    }; }

    private static String bg(String k) { return switch(k) {
        case "CONTROL DECK" -> "КОНТРОЛЕН ПАНЕЛ"; case "AUTOMATIC MEDIA SESSION" -> "АВТОМАТИЧНА МЕДИЙНА СЕСИЯ";
        case "DISPLAY" -> "ДИСТАНЦИЯ"; case "SYNC" -> "СИНХР."; case "KEEP AHEAD" -> "ПРЕДНИНА";
        case "SCREEN STICKY" -> "ЗА ЕКРАНА"; case "LYRICS" -> "ТЕКСТ НА ПЕСЕНТА"; case "CORNER LYRIC" -> "ТЕКСТ В ЪГЪЛА";
        case "WORD SYNC" -> "СИНХР. НА ДУМИ"; case "WORD SYNC MODE" -> "РЕЖИМ ЗА СИНХР."; case "HIGHLIGHT" -> "ОТКРОЯВАНЕ"; case "POP UP" -> "ИЗПЛУВАЩ ТЕКСТ"; case "POP" -> "POP"; case "STACK" -> "НАТРУПВАНЕ"; case "CHOOSE SAVED PROFILE" -> "ИЗБЕРИ ПРОФИЛ"; case "3D ANIM" -> "3D АНИМАЦИЯ"; case "3D PLATE" -> "3D ФОН";
        case "HUD CARD" -> "HUD ПАНЕЛ"; case "FACE CAMERA" -> "КЪМ КАМЕРАТА"; case "TEXT / THEME" -> "ТЕКСТ / ТЕМА";
        case "LRCLIB SOURCE" -> "ИЗТОЧНИК LRCLIB"; case "YT MUSIC SOURCE" -> "ИЗТОЧНИК YT MUSIC";
        case "YT MUSIC API" -> "YT MUSIC API";
        case "LANGUAGE" -> "ЕЗИК"; case "CLOSE / ESC" -> "ЗАТВОРИ / ESC"; case "ON" -> "ВКЛ"; case "OFF" -> "ИЗКЛ";
        case "TEXT / HOLOGRAM SETTINGS" -> "ТЕКСТ / ХОЛОГРАМНИ НАСТРОЙКИ"; case "FONT • EFFECTS • SKY • WORD WINDOW" -> "ШРИФТ • ЕФЕКТИ • НЕБЕ • ДУМИ";
        case "SKY TEXT" -> "ТЕКСТ В НЕБЕТО"; case "THEME" -> "ТЕМА"; case "FONT" -> "ШРИФТ"; case "TEXT SHADOW" -> "СЯНКА НА ТЕКСТА";
        case "TEXT STROKE" -> "КОНТУР НА ТЕКСТА"; case "3D BACKPLATE" -> "3D ФОН"; case "HUD BACKPLATE" -> "HUD ФОН";
        case "TEXT SIZE" -> "РАЗМЕР НА ТЕКСТА"; case "APPLY EXACT PIXELS" -> "ПРИЛОЖИ РАЗМЕРА";
        case "BACKPLATE OPACITY" -> "ПРОЗРАЧНОСТ НА ФОНА"; case "WORDS PER DISPLAY" -> "ДУМИ НА ПОКАЗВАНЕ";
        case "ALL" -> "ВСИЧКИ";
        case "TEXT COLOR / HEX" -> "ЦВЯТ НА ТЕКСТА / HEX"; case "BACK TO CONTROL DECK" -> "НАЗАД КЪМ ПАНЕЛА";
        case "TEXT COLOR" -> "ЦВЯТ НА ТЕКСТА"; case "SWATCHES / HEX VALUE" -> "ПАЛИТРА / HEX СТОЙНОСТ"; case "HEX COLOR" -> "HEX ЦВЯТ";
        case "APPLY COLOR" -> "ПРИЛОЖИ ЦВЯТ"; case "BACK TO CONTROLS" -> "НАЗАД КЪМ НАСТРОЙКИТЕ";
        case "SKY HEIGHT" -> "ВИСОЧИНА В НЕБЕТО"; case "FIXED SKY Y OFFSET" -> "ФИКСИРАН Y ОТСТЪП"; case "SKY Y ABOVE TORSO" -> "Y НАД ТОРСА"; case "DISTANCE" -> "ДИСТАНЦИЯ";
        case "PREVIOUS TEXT HOLD" -> "ЗАДЪРЖАНЕ НА СТАРИЯ ТЕКСТ";
        case "NO ACTIVE MEDIA SESSION" -> "НЯМА АКТИВНА МЕДИЙНА СЕСИЯ"; case "PLAY AUDIO IN SPOTIFY, YOUTUBE OR A BROWSER" -> "ПУСНЕТЕ АУДИО В SPOTIFY, YOUTUBE ИЛИ БРАУЗЪР";
        case "NOW PLAYING / LIVE LYRICS" -> "СЕГА СВИРИ / ТЕКСТ НА ПЕСЕНТА";
        case "Playing  •  lyrics synced" -> "СВИРИ • ТЕКСТЪТ Е СИНХРОНИЗИРАН"; case "Paused  •  lyrics hold position" -> "ПАУЗА • ТЕКСТЪТ Е СПРЯН";
        case "Finding synced lyrics…" -> "ТЪРСЕНЕ НА СИНХРОНИЗИРАН ТЕКСТ…"; case "No synced lyrics found" -> "НЕ Е НАМЕРЕН СИНХРОНИЗИРАН ТЕКСТ";
        case "Synced lyrics ready" -> "СИНХРОНИЗИРАНИЯТ ТЕКСТ Е ГОТОВ"; case "Lyrics hidden" -> "ТЕКСТЪТ Е СКРИТ";
        case "Waiting for a song to play" -> "В ОЧАКВАНЕ НА ПЕСЕН"; case "Waiting for Spotify, Apple Music or browser audio" -> "ОЧАКВАНЕ НА АУДИО ОТ SPOTIFY, APPLE MUSIC ИЛИ БРАУЗЪР";
        default -> k;
    }; }

    private static String de(String k) { return switch(k) {
        case "CONTROL DECK" -> "STEUERZENTRALE"; case "AUTOMATIC MEDIA SESSION" -> "AUTOMATISCHE MEDIENSITZUNG";
        case "DISPLAY" -> "ABSTAND"; case "SYNC" -> "SYNC"; case "KEEP AHEAD" -> "VORAUSBEHALTEN";
        case "SCREEN STICKY" -> "BILDSCHIRMFIXIERT"; case "LYRICS" -> "LIEDTEXT"; case "CORNER LYRIC" -> "ECKTEXT";
        case "WORD SYNC" -> "WORT-SYNC"; case "WORD SYNC MODE" -> "WORT-SYNC-MODUS"; case "HIGHLIGHT" -> "HERVORHEBEN"; case "POP UP" -> "EINBLENDEN"; case "POP" -> "POP"; case "STACK" -> "AUFBAUEN"; case "CHOOSE SAVED PROFILE" -> "PROFIL WÄHLEN"; case "3D ANIM" -> "3D-ANIM."; case "3D PLATE" -> "3D-PLATTE";
        case "HUD CARD" -> "HUD-KARTE"; case "FACE CAMERA" -> "KAMERA FOLGEN"; case "TEXT / THEME" -> "TEXT / DESIGN";
        case "LRCLIB SOURCE" -> "QUELLE LRCLIB"; case "YT MUSIC SOURCE" -> "QUELLE YT MUSIC";
        case "YT MUSIC API" -> "YT MUSIC API";
        case "LANGUAGE" -> "SPRACHE"; case "CLOSE / ESC" -> "SCHLIESSEN / ESC"; case "ON" -> "AN"; case "OFF" -> "AUS";
        case "TEXT / HOLOGRAM SETTINGS" -> "TEXT / HOLOGRAMM-EINSTELLUNGEN"; case "FONT • EFFECTS • SKY • WORD WINDOW" -> "SCHRIFT • EFFEKTE • HIMMEL • WÖRTER";
        case "SKY TEXT" -> "HIMMELSTEXT"; case "THEME" -> "DESIGN"; case "FONT" -> "SCHRIFT"; case "TEXT SHADOW" -> "TEXTSCHATTEN";
        case "TEXT STROKE" -> "TEXTKONTUR"; case "3D BACKPLATE" -> "3D-HINTERGRUND"; case "HUD BACKPLATE" -> "HUD-HINTERGRUND";
        case "TEXT SIZE" -> "TEXTGRÖSSE"; case "APPLY EXACT PIXELS" -> "PIXELGRÖSSE ÜBERNEHMEN";
        case "BACKPLATE OPACITY" -> "HINTERGRUND-DECKKRAFT"; case "WORDS PER DISPLAY" -> "WÖRTER PRO ANZEIGE";
        case "ALL" -> "ALLE";
        case "TEXT COLOR / HEX" -> "TEXTFARBE / HEX"; case "BACK TO CONTROL DECK" -> "ZURÜCK ZUR STEUERUNG";
        case "TEXT COLOR" -> "TEXTFARBE"; case "SWATCHES / HEX VALUE" -> "FARBFELDER / HEX-WERT"; case "HEX COLOR" -> "HEX-FARBE";
        case "APPLY COLOR" -> "FARBE ANWENDEN"; case "BACK TO CONTROLS" -> "ZURÜCK ZU DEN EINSTELLUNGEN";
        case "SKY HEIGHT" -> "HIMMELSHÖHE"; case "FIXED SKY Y OFFSET" -> "FESTER HIMMEL-Y-VERSATZ"; case "SKY Y ABOVE TORSO" -> "HÖHE ÜBER TORSO"; case "DISTANCE" -> "ABSTAND";
        case "PREVIOUS TEXT HOLD" -> "ALTE TEXTE ANZEIGEN";
        case "NO ACTIVE MEDIA SESSION" -> "KEINE AKTIVE MEDIENSITZUNG"; case "PLAY AUDIO IN SPOTIFY, YOUTUBE OR A BROWSER" -> "AUDIO IN SPOTIFY, YOUTUBE ODER EINEM BROWSER ABSPIELEN";
        case "NOW PLAYING / LIVE LYRICS" -> "JETZT LÄUFT / LIVE-TEXT";
        case "Playing  •  lyrics synced" -> "WIEDERGABE • TEXT SYNCHRON"; case "Paused  •  lyrics hold position" -> "PAUSE • TEXT ANGEHALTEN";
        case "Finding synced lyrics…" -> "SYNCHRONEN TEXT SUCHEN…"; case "No synced lyrics found" -> "KEIN SYNCHRONER TEXT GEFUNDEN";
        case "Synced lyrics ready" -> "SYNCHRONER TEXT BEREIT"; case "Lyrics hidden" -> "TEXT AUSGEBLENDET";
        case "Waiting for a song to play" -> "WARTET AUF MUSIKWIEDERGABE"; case "Waiting for Spotify, Apple Music or browser audio" -> "WARTET AUF SPOTIFY-, APPLE-MUSIC- ODER BROWSER-AUDIO";
        default -> k;
    }; }

    private static String nl(String k) { return switch(k) {
        case "CONTROL DECK" -> "BEDIENINGSPANEEL"; case "AUTOMATIC MEDIA SESSION" -> "AUTOMATISCHE MEDIASESSIE";
        case "DISPLAY" -> "AFSTAND"; case "SYNC" -> "SYNC"; case "KEEP AHEAD" -> "VOORUIT HOUDEN";
        case "SCREEN STICKY" -> "SCHERMVAST"; case "LYRICS" -> "SONGTEKST"; case "CORNER LYRIC" -> "HOEKTEKST";
        case "WORD SYNC" -> "WOORD-SYNC"; case "WORD SYNC MODE" -> "WOORD-SYNC-MODUS"; case "HIGHLIGHT" -> "MARKEREN"; case "POP UP" -> "OPPOPEN"; case "POP" -> "POP"; case "STACK" -> "OPBOUWEN"; case "CHOOSE SAVED PROFILE" -> "PROFIEL KIEZEN"; case "3D ANIM" -> "3D-ANIMATIE"; case "3D PLATE" -> "3D-ACHTERGROND";
        case "HUD CARD" -> "HUD-KAART"; case "FACE CAMERA" -> "NAAR CAMERA"; case "TEXT / THEME" -> "TEKST / THEMA";
        case "LRCLIB SOURCE" -> "BRON LRCLIB"; case "YT MUSIC SOURCE" -> "BRON YT MUSIC";
        case "YT MUSIC API" -> "YT MUSIC API";
        case "LANGUAGE" -> "TAAL"; case "CLOSE / ESC" -> "SLUITEN / ESC"; case "ON" -> "AAN"; case "OFF" -> "UIT";
        case "TEXT / HOLOGRAM SETTINGS" -> "TEKST / HOLOGRAMINSTELLINGEN"; case "FONT • EFFECTS • SKY • WORD WINDOW" -> "LETTERTYPE • EFFECTEN • LUCHT • WOORDEN";
        case "SKY TEXT" -> "TEKST IN DE LUCHT"; case "THEME" -> "THEMA"; case "FONT" -> "LETTERTYPE"; case "TEXT SHADOW" -> "TEKSTSCHADUW";
        case "TEXT STROKE" -> "TEKSTOMLIJNING"; case "3D BACKPLATE" -> "3D-ACHTERGROND"; case "HUD BACKPLATE" -> "HUD-ACHTERGROND";
        case "TEXT SIZE" -> "TEKSTGROOTTE"; case "APPLY EXACT PIXELS" -> "PIXELFORMAAT TOEPASSEN";
        case "BACKPLATE OPACITY" -> "ACHTERGRONDONDOORZICHTIGHEID"; case "WORDS PER DISPLAY" -> "WOORDEN PER WEERGAVE";
        case "ALL" -> "ALLES";
        case "TEXT COLOR / HEX" -> "TEKSTKLEUR / HEX"; case "BACK TO CONTROL DECK" -> "TERUG NAAR BEDIENING";
        case "TEXT COLOR" -> "TEKSTKLEUR"; case "SWATCHES / HEX VALUE" -> "KLEURVAKKEN / HEX-WAARDE"; case "HEX COLOR" -> "HEX-KLEUR";
        case "APPLY COLOR" -> "KLEUR TOEPASSEN"; case "BACK TO CONTROLS" -> "TERUG NAAR INSTELLINGEN";
        case "SKY HEIGHT" -> "HOOGTE IN DE LUCHT"; case "FIXED SKY Y OFFSET" -> "VASTE HEMEL-Y-OFFSET"; case "SKY Y ABOVE TORSO" -> "HOOGTE BOVEN TORSO"; case "DISTANCE" -> "AFSTAND";
        case "PREVIOUS TEXT HOLD" -> "OUDE TEKST BEHOUDEN";
        case "NO ACTIVE MEDIA SESSION" -> "GEEN ACTIEVE MEDIASESSIE"; case "PLAY AUDIO IN SPOTIFY, YOUTUBE OR A BROWSER" -> "SPEEL AUDIO AF IN SPOTIFY, YOUTUBE OF EEN BROWSER";
        case "NOW PLAYING / LIVE LYRICS" -> "NU AAN HET SPELEN / LIVE SONGTEKST";
        case "Playing  •  lyrics synced" -> "AFSPELEN • TEKST GESYNCHRONISEERD"; case "Paused  •  lyrics hold position" -> "GEPAUZEERD • TEKST STAAT STIL";
        case "Finding synced lyrics…" -> "GESYNCHRONISEERDE TEKST ZOEKEN…"; case "No synced lyrics found" -> "GEEN GESYNCHRONISEERDE TEKST GEVONDEN";
        case "Synced lyrics ready" -> "GESYNCHRONISEERDE TEKST GEREED"; case "Lyrics hidden" -> "SONGTEKST VERBORGEN";
        case "Waiting for a song to play" -> "WACHTEN OP MUZIEK"; case "Waiting for Spotify, Apple Music or browser audio" -> "WACHTEN OP AUDIO VAN SPOTIFY, APPLE MUSIC OF EEN BROWSER";
        default -> k;
    }; }

    private HoloText() {}
}
