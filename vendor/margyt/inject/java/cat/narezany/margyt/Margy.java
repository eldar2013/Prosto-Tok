package cat.narezany.margyt;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * The mod's settings, and the one piece of context it needs to read them.
 *
 * Nothing calls an init() from the app's startup: there is no hook to put one
 * in without patching TikTok's Application class, and that class moves between
 * releases. The context comes from ActivityThread instead, which every Android
 * process has had since 2008 and which is what Application itself is built on.
 */
public final class Margy {

    private Margy() {}

    public static final String PREFS = "margyt";
    public static final String KEY_ENABLED = "region_enabled";
    public static final String KEY_COUNTRY = "region_country";

    public static final String DEFAULT_ISO = "nl";

    /** iso, mcc+mnc, carrier, display name */
    public static final String[][] COUNTRIES = {
            {"nl", "20408",  "KPN",        "Netherlands"},
            {"us", "310410", "AT&T",       "United States"},
            {"gb", "23430",  "EE",         "United Kingdom"},
            {"de", "26201",  "Telekom",    "Germany"},
            {"fr", "20801",  "Orange",     "France"},
            {"es", "21401",  "Movistar",   "Spain"},
            {"it", "22201",  "TIM",        "Italy"},
            {"se", "24001",  "Telia",      "Sweden"},
            {"pl", "26003",  "Orange",     "Poland"},
            {"ua", "25503",  "Kyivstar",   "Ukraine"},
            {"kz", "40101",  "Beeline",    "Kazakhstan"},
            {"ru", "25001",  "MTS",        "Russia"},
            {"tr", "28601",  "Turkcell",   "Turkey"},
            {"br", "72406",  "Vivo",       "Brazil"},
            {"mx", "33403",  "Telcel",     "Mexico"},
            {"ca", "302220", "Telus",      "Canada"},
            {"au", "50501",  "Telstra",    "Australia"},
            {"jp", "44010",  "NTT Docomo", "Japan"},
            {"kr", "45005",  "SK Telecom", "South Korea"},
            {"in", "40410",  "Airtel",     "India"},
            {"id", "51010",  "Telkomsel",  "Indonesia"},
            {"vn", "45201",  "Viettel",    "Vietnam"},
            {"th", "52001",  "AIS",        "Thailand"},
            {"ph", "51502",  "Globe",      "Philippines"},

            {"by", "25701",  "A1",         "Belarus"},
            {"md", "25901",  "Orange",     "Moldova"},
            {"ge", "28201",  "Geocell",    "Georgia"},
            {"am", "28301",  "Beeline",    "Armenia"},
            {"az", "40001",  "Azercell",   "Azerbaijan"},
            {"uz", "43404",  "Beeline",    "Uzbekistan"},
            {"kg", "43701",  "Beeline",    "Kyrgyzstan"},
            {"tj", "43601",  "Babilon",    "Tajikistan"},
            {"lt", "24601",  "Telia",      "Lithuania"},
            {"lv", "24701",  "LMT",        "Latvia"},
            {"ee", "24801",  "Telia",      "Estonia"},
            {"fi", "24491",  "Elisa",      "Finland"},
            {"no", "24201",  "Telenor",    "Norway"},
            {"dk", "23801",  "TDC",        "Denmark"},
            {"ie", "27201",  "Vodafone",   "Ireland"},
            {"pt", "26801",  "Vodafone",   "Portugal"},
            {"gr", "20201",  "Cosmote",    "Greece"},
            {"cz", "23001",  "T-Mobile",   "Czechia"},
            {"sk", "23101",  "Orange",     "Slovakia"},
            {"hu", "21630",  "Telekom",    "Hungary"},
            {"ro", "22601",  "Vodafone",   "Romania"},
            {"bg", "28401",  "A1",         "Bulgaria"},
            {"rs", "22003",  "Telekom",    "Serbia"},
            {"hr", "21901",  "T-Mobile",   "Croatia"},
            {"at", "23201",  "A1",         "Austria"},
            {"ch", "22801",  "Swisscom",   "Switzerland"},
            {"be", "20601",  "Proximus",   "Belgium"},
            {"il", "42501",  "Partner",    "Israel"},
            {"ae", "42402",  "Etisalat",   "United Arab Emirates"},
            {"sa", "42001",  "STC",        "Saudi Arabia"},
            {"eg", "60201",  "Orange",     "Egypt"},
            {"za", "65510",  "Vodacom",    "South Africa"},
            {"ng", "62130",  "MTN",        "Nigeria"},
            {"ar", "72234",  "Personal",   "Argentina"},
            {"cl", "73001",  "Entel",      "Chile"},
            {"co", "73201",  "Claro",      "Colombia"},
            {"pe", "71606",  "Movistar",   "Peru"},
            {"nz", "53001",  "Vodafone",   "New Zealand"},
            {"my", "50212",  "Maxis",      "Malaysia"},
            {"sg", "52501",  "Singtel",    "Singapore"},
            {"pk", "41001",  "Mobilink",   "Pakistan"},
            {"bd", "47001",  "Grameenphone", "Bangladesh"},
            {"cn", "46000", "China Mobile", "China"}, {"tw", "46692", "Chunghwa", "Taiwan"},
            {"hk", "45400", "CSL", "Hong Kong"}, {"mo", "45501", "CTM", "Macau"},
            {"mn", "42899", "Mobicom", "Mongolia"}, {"lk", "41302", "Dialog", "Sri Lanka"},
            {"np", "42901", "Nepal Telecom", "Nepal"}, {"mm", "41401", "MPT", "Myanmar"},
            {"kh", "45601", "Cellcard", "Cambodia"}, {"la", "45701", "LTC", "Laos"},
            {"af", "41201", "AWCC", "Afghanistan"}, {"ir", "43211", "MCI", "Iran"},
            {"iq", "41820", "Zain", "Iraq"}, {"jo", "41601", "Zain", "Jordan"},
            {"lb", "41501", "Alfa", "Lebanon"}, {"sy", "41701", "Syriatel", "Syria"},
            {"kw", "41902", "Zain", "Kuwait"}, {"qa", "42701", "Ooredoo", "Qatar"},
            {"bh", "42601", "Batelco", "Bahrain"}, {"om", "42202", "Omantel", "Oman"},
            {"ye", "42101", "SabaFon", "Yemen"}, {"cy", "28001", "Cytamobile", "Cyprus"},
            {"mt", "27801", "Vodafone", "Malta"}, {"is", "27401", "Siminn", "Iceland"},
            {"lu", "27001", "POST", "Luxembourg"}, {"si", "29341", "Telekom", "Slovenia"},
            {"ba", "21890", "BH Telecom", "Bosnia and Herzegovina"}, {"me", "29701", "One", "Montenegro"},
            {"mk", "29401", "Telekom", "North Macedonia"}, {"al", "27601", "One", "Albania"},
            {"ma", "60400", "Orange", "Morocco"}, {"dz", "60301", "Mobilis", "Algeria"},
            {"tn", "60502", "Tunisie Telecom", "Tunisia"}, {"ly", "60600", "Libyana", "Libya"},
            {"sd", "63401", "Zain", "Sudan"}, {"et", "63601", "Ethio Telecom", "Ethiopia"},
            {"ke", "63902", "Safaricom", "Kenya"}, {"tz", "64004", "Vodacom", "Tanzania"},
            {"ug", "64101", "Airtel", "Uganda"}, {"rw", "63510", "MTN", "Rwanda"},
            {"gh", "62001", "MTN", "Ghana"}, {"ci", "61203", "Orange", "Cote d'Ivoire"},
            {"sn", "60801", "Orange", "Senegal"}, {"cm", "62401", "MTN", "Cameroon"},
            {"ao", "63102", "Unitel", "Angola"}, {"zm", "64501", "Airtel", "Zambia"},
            {"zw", "64804", "Econet", "Zimbabwe"}, {"mz", "64304", "Vodacom", "Mozambique"},
            {"mg", "64604", "Telma", "Madagascar"}, {"cd", "63001", "Vodacom", "DR Congo"},
            {"ve", "73404", "Movistar", "Venezuela"}, {"ec", "74000", "Movistar", "Ecuador"},
            {"bo", "73602", "Entel", "Bolivia"}, {"py", "74402", "Claro", "Paraguay"},
            {"uy", "74801", "Antel", "Uruguay"}, {"cr", "71201", "Kolbi", "Costa Rica"},
            {"pa", "71401", "+Movil", "Panama"}, {"gt", "70401", "Claro", "Guatemala"},
            {"hn", "70802", "Tigo", "Honduras"}, {"sv", "70601", "Claro", "El Salvador"},
            {"ni", "71021", "Claro", "Nicaragua"}, {"do", "37002", "Claro", "Dominican Republic"},
            {"cu", "36801", "Cubacel", "Cuba"}, {"jm", "338050", "Digicel", "Jamaica"},
            {"pr", "330110", "Claro", "Puerto Rico"}, {"tt", "37412", "bmobile", "Trinidad and Tobago"},
            {"fj", "54201", "Vodafone", "Fiji"}, {"pg", "53703", "Digicel", "Papua New Guinea"},
            {"tm", "43801", "MTS", "Turkmenistan"}, {"mv", "47201", "Dhiraagu", "Maldives"},
            {"bt", "40211", "B-Mobile", "Bhutan"}, {"bn", "52811", "DST", "Brunei"},
            {"gl", "29001", "Tusass", "Greenland"}, {"ad", "21303", "Andorra Telecom", "Andorra"},
            {"mc", "21201", "Monaco Telecom", "Monaco"}, {"li", "29501", "Swisscom", "Liechtenstein"},
            {"sm", "29201", "TIM", "San Marino"},
            // not on any map: 001-01 is the test network every phone knows
            {"xx", "00101", "Двойка", "Страна невыученных уроков"},
            {"aq", "00103", "Penguin Mobile", "Antarctica"},
            {"xa", "00102", "Poseidon", "Atlantis"},
            {"xp", "99999", "Jolly Roger", "Pirate Republic"},
    };

    public static final int ISO = 0, MCCMNC = 1, CARRIER = 2, LABEL = 3;

    /** The country's name in the language the phone speaks, the English one where it has none. */
    public static String name(String[] row) {
        if ("xx".equals(row[ISO])) return Local.of("Страна невыученных уроков");
        if ("xa".equals(row[ISO])) return Local.of("Атлантида");
        if ("xp".equals(row[ISO])) return Local.of("Пиратская республика");
        try {
            String shown = new java.util.Locale("", row[ISO].toUpperCase(java.util.Locale.ROOT))
                    .getDisplayCountry(java.util.Locale.getDefault());
            if (shown != null && shown.length() > 0 && !shown.equalsIgnoreCase(row[ISO])) return shown;
        } catch (Throwable ignored) {
        }
        return row[LABEL];
    }

    private static volatile Context sContext;

    // ------------------------------------------------------------- context

    public static Context context() {
        Context known = sContext;
        if (known != null) return known;
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Object app = thread.getMethod("currentApplication").invoke(null);
            if (app instanceof Application) {
                sContext = (Context) app;
                warmLikes();
            }
        } catch (Throwable ignored) {
            // too early in startup, or a stripped-down runtime: fall back to
            // the defaults until someone asks again
        }
        return sContext;
    }

    private static volatile boolean likesWarmed;

    /** Recolour the like animations once at start, off the UI thread. */
    private static void warmLikes() {
        if (likesWarmed) return;
        likesWarmed = true;
        try { Accent.prewarmLikeAnimations(null); } catch (Throwable ignored) { }
    }

    /** Set from the settings screen, where a context is never in doubt. */
    public static void attach(Context context) {
        if (context != null) {
            sContext = context.getApplicationContext();
            warmLikes();
        }
    }

    private static SharedPreferences prefs() {
        Context context = context();
        if (context == null) return null;
        try {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------ settings

    public static boolean isEnabled() {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(boolean enabled) {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static String iso() {
        SharedPreferences p = prefs();
        String iso = p == null ? DEFAULT_ISO : p.getString(KEY_COUNTRY, DEFAULT_ISO);
        return row(iso)[ISO];
    }

    public static void setIso(String iso) {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putString(KEY_COUNTRY, iso).apply();
    }

    /** The row for `iso`, or the default one when the list has never heard of it. */
    public static String[] row(String iso) {
        if (iso != null) {
            for (String[] country : COUNTRIES) {
                if (country[ISO].equals(iso)) return country;
            }
        }
        for (String[] country : COUNTRIES) {
            if (country[ISO].equals(DEFAULT_ISO)) return country;
        }
        return COUNTRIES[0];
    }

    private static volatile String[] frozen;
    private static volatile Boolean frozenOn;

    /**
     * The row the mod reports, decided once per run.
     *
     * A phone's SIM does not change country while an app is open. Reading the
     * setting on every call meant ours did. The settings already ask for a
     * restart when something changes.
     */
    public static String[] current() {
        String[] known = frozen;
        if (known != null) return known;
        known = row(iso());
        frozen = known;
        return known;
    }

    /** True when the mod should be answering for the device at all. */
    public static boolean active() {
        if (context() == null) return false;
        Boolean known = frozenOn;
        if (known != null) return known;
        boolean on = isEnabled();
        frozenOn = on;
        return on;
    }
}
