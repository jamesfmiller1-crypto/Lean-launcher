package nl.home.leanlauncher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.InputType;
import android.widget.EditText;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Blocky pixel-world TV home screen. Your apps are blocks in an animated landscape.
 * No recommendations, no tracking. The only network use is the weather (open-meteo.com).
 */
public class MainActivity extends Activity {

    /** Apps put on the home screen automatically (matched on name), in this order. */
    private static final String[] DEFAULT_APPS = {
            "smarttube", "youtube", "netflix", "rtl", "iptv extreme", "premiumize", "kodi"
    };

    private static final int SHADOW = Color.parseColor("#222222");

    private PackageManager pm;
    private SharedPreferences prefs;
    private Map<String, ResolveInfo> apps = new LinkedHashMap<>();

    private WorldView world;
    private View homeView;
    private ScrollView allAppsView;
    private LinearLayout favRow;
    private LinearLayout allAppsList;
    private TextView tip;
    private TextView weatherText;
    private long lastWeather = 0;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);

        FrameLayout root = new FrameLayout(this);
        world = new WorldView(this);
        homeView = buildHome();
        allAppsView = buildAllApps();
        allAppsView.setVisibility(View.GONE);
        root.addView(world, match());
        root.addView(homeView, match());
        root.addView(allAppsView, match());
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        world.setRunning(true);
        loadApps();
        refreshHome();
        if (allAppsView.getVisibility() == View.VISIBLE) refreshAllApps();
        loadWeather();
    }

    @Override
    protected void onPause() {
        super.onPause();
        world.setRunning(false); // no animation (and no CPU use) while an app is open
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        showHome();
    }

    @Override
    public void onBackPressed() {
        if (allAppsView.getVisibility() == View.VISIBLE) showHome();
    }

    // ---------------------------------------------------------------- apps

    private void loadApps() {
        apps = new LinkedHashMap<>();
        addApps(Intent.CATEGORY_LEANBACK_LAUNCHER);
        addApps(Intent.CATEGORY_LAUNCHER);
        apps.remove(getPackageName());
    }

    private void addApps(String category) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!apps.containsKey(pkg)) apps.put(pkg, ri);
        }
    }

    private String label(ResolveInfo ri) {
        return String.valueOf(ri.loadLabel(pm));
    }

    private List<ResolveInfo> sortedApps() {
        List<ResolveInfo> list = new ArrayList<>(apps.values());
        list.sort((a, b) -> label(a).compareToIgnoreCase(label(b)));
        return list;
    }

    private void launch(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) {
            toast("Can't open this app");
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("Can't open this app");
        }
    }

    // ---------------------------------------------------------------- favourites

    private List<String> getFavs() {
        if (!prefs.getBoolean("edited", false)) {
            List<String> favs = new ArrayList<>();
            List<ResolveInfo> all = sortedApps();
            for (String key : DEFAULT_APPS) {
                for (ResolveInfo ri : all) {
                    String pkg = ri.activityInfo.packageName;
                    String name = label(ri).toLowerCase(Locale.ROOT);
                    if (!favs.contains(pkg)
                            && (name.contains(key) || pkg.contains(key.replace(" ", "")))) {
                        favs.add(pkg);
                        break;
                    }
                }
            }
            return favs;
        }
        String s = prefs.getString("favs", "");
        List<String> favs = new ArrayList<>();
        if (!s.isEmpty()) favs.addAll(Arrays.asList(s.split(",")));
        return favs;
    }

    private void saveFavs(List<String> favs) {
        prefs.edit()
                .putString("favs", TextUtils.join(",", favs))
                .putBoolean("edited", true)
                .apply();
    }

    private void editFavourite(String pkg, String name) {
        String[] options = {"Move left", "Move right", "Remove from home"};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(name)
                .setItems(options, (d, which) -> {
                    List<String> favs = getFavs();
                    int pos = favs.indexOf(pkg);
                    if (pos < 0) return;
                    if (which == 0 && pos > 0) {
                        favs.remove(pos);
                        favs.add(pos - 1, pkg);
                    } else if (which == 1 && pos < favs.size() - 1) {
                        favs.remove(pos);
                        favs.add(pos + 1, pkg);
                    } else if (which == 2) {
                        favs.remove(pos);
                    }
                    saveFavs(favs);
                    refreshHome();
                })
                .show();
    }

    private void toggleFavourite(String pkg, String name) {
        List<String> favs = getFavs();
        if (favs.remove(pkg)) {
            toast(name + " removed from home");
        } else {
            favs.add(pkg);
            toast(name + " added to home");
        }
        saveFavs(favs);
        refreshHome();
    }

    // ---------------------------------------------------------------- home screen

    private View buildHome() {
        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(dp(48), dp(28), dp(48), dp(28));
        home.setClipChildren(false);
        home.setClipToPadding(false);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        pixelText(clock, 52);
        left.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        pixelText(date, 18);
        left.addView(date);

        top.addView(left);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        weatherText = new TextView(this);
        pixelText(weatherText, 22);
        weatherText.setGravity(Gravity.END);
        top.addView(weatherText);
        home.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        home.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        tip = new TextView(this);
        pixelText(tip, 18);
        tip.setBackgroundColor(Color.argb(220, 20, 20, 20));
        tip.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.gravity = Gravity.CENTER_HORIZONTAL;
        tipLp.bottomMargin = dp(8);
        home.addView(tip, tipLp);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        favRow = new LinearLayout(this);
        favRow.setOrientation(LinearLayout.HORIZONTAL);
        favRow.setPadding(dp(16), dp(24), dp(16), dp(12));
        favRow.setClipChildren(false);
        favRow.setClipToPadding(false);
        scroll.addView(favRow);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.gravity = Gravity.CENTER_HORIZONTAL;
        home.addView(scroll, rowLp);
        return home;
    }

    private void refreshHome() {
        favRow.removeAllViews();
        int seed = 1;
        for (String pkg : getFavs()) {
            ResolveInfo ri = apps.get(pkg);
            if (ri == null) continue;
            String name = label(ri);
            favRow.addView(block(BlockDrawable.GRASS, seed++, ri, name, 100,
                    v -> launch(pkg),
                    v -> { editFavourite(pkg, name); return true; }));
        }
        favRow.addView(block(BlockDrawable.STONE, seed++, null, "All apps", 100,
                v -> showAllApps(), null));
        favRow.addView(block(BlockDrawable.GOLD, seed++, null, "Mainte-\nnance", 100,
                v -> showMaintenance(), null));
        favRow.addView(block(BlockDrawable.STONE, seed, null, "Settings", 100,
                v -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (Exception e) {
                        toast("Settings not available");
                    }
                }, null));
        if (homeView.getVisibility() == View.VISIBLE && favRow.getChildCount() > 0) {
            favRow.getChildAt(0).requestFocus();
        }
    }

    private void showHome() {
        allAppsView.setVisibility(View.GONE);
        homeView.setVisibility(View.VISIBLE);
        if (favRow.getChildCount() > 0) favRow.getChildAt(0).requestFocus();
    }

    // ---------------------------------------------------------------- maintenance

    private void showMaintenance() {
        String title = "Maintenance";
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long free = fs.getAvailableBytes() / (1024 * 1024);
            long total = fs.getTotalBytes() / (1024 * 1024);
            long pct = total > 0 ? 100 - (free * 100 / total) : 0;
            title = "Storage: " + free + " MB free of " + total + " MB (" + pct + "% used)"
                    + (pct >= 93 ? "  - very full!" : "");
        } catch (Exception ignored) {
        }
        String city = prefs.getString("city", null);
        String[] options = {
                "Storage settings",
                "Manage apps (clear cache or uninstall)",
                city == null ? "Set weather city" : "Change weather city (" + city + ")"
        };
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setItems(options, (d, which) -> {
                    if (which == 0) openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
                    else if (which == 1) openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                    else askCity();
                })
                .show();
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e2) {
                toast("Settings not available");
            }
        }
    }

    // ---------------------------------------------------------------- weather

    private void askCity() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("City name");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Weather city")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> findCity(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void findCity(final String name) {
        if (name.isEmpty()) return;
        new Thread(() -> {
            try {
                String json = httpGet("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                        + URLEncoder.encode(name, "UTF-8"));
                JSONObject r = new JSONObject(json).getJSONArray("results").getJSONObject(0);
                prefs.edit()
                        .putString("city", r.getString("name"))
                        .putString("lat", String.valueOf(r.getDouble("latitude")))
                        .putString("lon", String.valueOf(r.getDouble("longitude")))
                        .apply();
                lastWeather = 0;
                runOnUiThread(this::loadWeather);
            } catch (Exception e) {
                runOnUiThread(() -> toast("Couldn't find that city"));
            }
        }).start();
    }

    private void loadWeather() {
        final String lat = prefs.getString("lat", null);
        final String lon = prefs.getString("lon", null);
        final String city = prefs.getString("city", "");
        if (lat == null || lon == null) {
            weatherText.setText("Weather: set your city\nin Maintenance");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastWeather < 30 * 60 * 1000) return; // at most once per half hour
        lastWeather = now;
        new Thread(() -> {
            try {
                String json = httpGet("https://api.open-meteo.com/v1/forecast?latitude=" + lat
                        + "&longitude=" + lon + "&current=temperature_2m,weather_code");
                JSONObject cur = new JSONObject(json).getJSONObject("current");
                final long temp = Math.round(cur.getDouble("temperature_2m"));
                final int code = cur.getInt("weather_code");
                runOnUiThread(() -> showWeather(city, temp, code));
            } catch (Exception e) {
                lastWeather = 0;
                runOnUiThread(() -> weatherText.setText(city + "\nweather unavailable"));
            }
        }).start();
    }

    private void showWeather(String city, long temp, int code) {
        String desc;
        int kind;
        if (code <= 1) { desc = "Clear"; kind = 0; }
        else if (code <= 3) { desc = code == 2 ? "Partly cloudy" : "Cloudy"; kind = 1; }
        else if (code <= 48) { desc = "Fog"; kind = 1; }
        else if (code <= 67) { desc = code <= 57 ? "Drizzle" : "Rain"; kind = 2; }
        else if (code <= 77) { desc = "Snow"; kind = 3; }
        else if (code <= 82) { desc = "Showers"; kind = 2; }
        else if (code <= 86) { desc = "Snow showers"; kind = 3; }
        else { desc = "Thunderstorm"; kind = 2; }
        weatherText.setText(temp + "\u00B0C  " + desc + "\n" + city);
        world.setWeather(kind);
    }

    private String httpGet(String address) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(address).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    // ---------------------------------------------------------------- all apps

    private ScrollView buildAllApps() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.argb(200, 14, 17, 23));
        scroll.setClipChildren(false);
        allAppsList = new LinearLayout(this);
        allAppsList.setOrientation(LinearLayout.VERTICAL);
        allAppsList.setPadding(dp(56), dp(32), dp(56), dp(32));
        allAppsList.setClipChildren(false);
        scroll.addView(allAppsList);
        return scroll;
    }

    private void refreshAllApps() {
        allAppsList.removeAllViews();

        TextView title = new TextView(this);
        title.setText("All apps");
        pixelText(title, 30);
        allAppsList.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Hold OK on an app to add it to (or remove it from) the home screen");
        pixelText(hint, 14);
        hint.setPadding(0, 0, 0, dp(16));
        allAppsList.addView(hint);

        LinearLayout row = null;
        int perRow = 7;
        List<ResolveInfo> list = sortedApps();
        for (int i = 0; i < list.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setPadding(0, dp(8), 0, dp(8));
                allAppsList.addView(row);
            }
            ResolveInfo ri = list.get(i);
            String pkg = ri.activityInfo.packageName;
            String name = label(ri);
            row.addView(block(BlockDrawable.GRASS, i + 10, ri, name, 100,
                    v -> launch(pkg),
                    v -> { toggleFavourite(pkg, name); return true; }));
        }
    }

    private void showAllApps() {
        refreshAllApps();
        homeView.setVisibility(View.GONE);
        allAppsView.setVisibility(View.VISIBLE);
        allAppsView.scrollTo(0, 0);
        if (allAppsList.getChildCount() > 2) {
            View firstRow = allAppsList.getChildAt(2);
            if (firstRow instanceof ViewGroup && ((ViewGroup) firstRow).getChildCount() > 0) {
                ((ViewGroup) firstRow).getChildAt(0).requestFocus();
            }
        }
    }

    // ---------------------------------------------------------------- blocks

    private View block(int type, int seed, ResolveInfo ri, String name, int size,
                       View.OnClickListener click, View.OnLongClickListener longClick) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(new BlockDrawable(type, seed));
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setClickable(true);
        tile.setOnClickListener(click);
        if (longClick != null) tile.setOnLongClickListener(longClick);

        if (ri != null) {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(ri.loadIcon(pm));
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(size / 2), dp(size / 2));
            ilp.gravity = Gravity.CENTER;
            tile.addView(icon, ilp);
        } else {
            TextView t = new TextView(this);
            t.setText(name);
            pixelText(t, 15);
            t.setGravity(Gravity.CENTER);
            tile.addView(t, match());
        }

        final ValueAnimator[] bob = new ValueAnimator[1];
        tile.setOnFocusChangeListener((v, focused) -> {
            if (bob[0] != null) bob[0].cancel();
            if (focused) {
                tip.setText(name.replace("-\n", ""));
                world.onSelect();
                v.setScaleX(1.12f);
                v.setScaleY(1.12f);
                v.setElevation(dp(8));
                bob[0] = ValueAnimator.ofFloat(-dp(8), -dp(12));
                bob[0].setDuration(500);
                bob[0].setRepeatMode(ValueAnimator.REVERSE);
                bob[0].setRepeatCount(ValueAnimator.INFINITE);
                bob[0].addUpdateListener(a -> v.setTranslationY((float) a.getAnimatedValue()));
                bob[0].start();
            } else {
                v.setScaleX(1f);
                v.setScaleY(1f);
                v.setElevation(0);
                v.setTranslationY(0);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), dp(size));
        lp.setMargins(dp(8), 0, dp(8), 0);
        tile.setLayoutParams(lp);
        return tile;
    }

    // ---------------------------------------------------------------- helpers

    private void pixelText(TextView t, int sp) {
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        t.setShadowLayer(1, dp(2), dp(2), SHADOW);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
