package com.ikoko.gilauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Cyber-gold home + lock for Redmi 9A (armeabi-v7a, API 29).
 * Pure Java, no native libs, so it installs on 32-bit MIUI.
 */
public class HomeActivity extends Activity {
    private static final String[][] DOCK = new String[][]{
            {"com.google.android.dialer", "com.android.dialer", "com.android.contacts", "com.samsung.android.dialer"},
            {"com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging", "com.android.messaging"},
            {"com.android.chrome", "com.android.browser", "com.mi.globalbrowser", "org.mozilla.firefox"},
            {"com.android.camera", "com.android.camera2", "com.mediatek.camera", "org.codeaurora.snapcam"}
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Item> all = new ArrayList<Item>();
    private final List<Item> shown = new ArrayList<Item>();
    private final List<Item> dockItems = new ArrayList<Item>();
    private final ArrayDeque<Item> iconQueue = new ArrayDeque<Item>();
    private final LruCache<String, Bitmap> icons = new LruCache<String, Bitmap>(140);

    private FrameLayout root;
    private LockLayer lockLayer;
    private View settingsPanel;
    private GridView grid;
    private LinearLayout dock;
    private EditText search;
    private TextView empty;
    private TextView homeTime;
    private TextView lockTime;
    private TextView lockSeconds;
    private TextView homeDate;
    private TextView lockDate;
    private TextView banner;
    private TextView lockToggle;
    private AppAdapter adapter;
    private PackageManager pm;
    private SharedPreferences prefs;
    private Typeface displayFont;
    private BroadcastReceiver screenReceiver;
    private Bitmap placeholder;
    private int iconPx;
    private String query = "";
    private boolean armed = true;
    private boolean lockEnabled = true;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable iconPump = new Runnable() {
        @Override
        public void run() {
            int made = 0;
            while (made < 2 && !iconQueue.isEmpty()) {
                Item it = iconQueue.poll();
                if (it == null || icons.get(it.pkg) != null) continue;
                Bitmap bmp;
                try {
                    Drawable d = pm.getActivityIcon(new ComponentName(it.pkg, it.cls));
                    bmp = IconFactory.frame(d, iconPx);
                } catch (Exception e) {
                    bmp = IconFactory.letter(it.label, iconPx);
                }
                icons.put(it.pkg, bmp);
                made++;
            }
            paintIcons();
            if (!iconQueue.isEmpty()) handler.post(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        prefs = getSharedPreferences("gi", MODE_PRIVATE);
        lockEnabled = prefs.getBoolean("lock", true);
        armed = lockEnabled;
        try {
            displayFont = Typeface.createFromAsset(getAssets(), "fonts/Orbitron.ttf");
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                displayFont = Typeface.create(displayFont, 700, false);
            }
        } catch (RuntimeException e) {
            displayFont = Typeface.MONOSPACE;
        }
        iconPx = dp(62);
        placeholder = IconFactory.letter("G", iconPx);
        setupWindow();
        buildUi();
        setContentView(root);
        if (!lockEnabled) {
            armed = false;
            lockLayer.setVisibility(View.GONE);
        }
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) && lockEnabled) {
                    armed = true;
                    showLock();
                }
            }
        };
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startLoad();
            }
        }, 40);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(clockTick);
        refreshBanner();
        if (lockEnabled && armed) showLock();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(clockTick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
            screenReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        closeSettings();
        if (search != null) search.setText("");
    }

    @Override
    public void onBackPressed() {
        if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE) {
            closeSettings();
            return;
        }
        if (search != null && search.hasFocus()) {
            hideKeyboard();
            search.clearFocus();
            return;
        }
        if (lockEnabled && lockLayer.getVisibility() != View.VISIBLE) {
            armed = true;
            showLock();
        }
    }

    private void setupWindow() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.parseColor("#07060A"));
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF07060A);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
                return insets;
            }
        });

        CyberBackdrop backdrop = new CyberBackdrop(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(14), dp(8), dp(14), dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView sys = ghost("SYS");
        sys.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
        TextView lockBtn = ghost("LOCK");
        lockBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                armed = true;
                showLock();
            }
        });
        LinearLayout.LayoutParams side = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        top.addView(sys, side);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(lockBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(top);

        homeTime = clockText(28);
        column.addView(homeTime);
        column.addView(wordmark(34));
        column.addView(byline());

        search = new EditText(this);
        search.setHint("ရှာရန်");
        search.setHintTextColor(0xFFA89878);
        search.setTextColor(0xFFF6EDD8);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setInputType(EditorInfo.TYPE_CLASS_TEXT);
        GradientDrawable field = new GradientDrawable();
        field.setColor(0xCC120E0B);
        field.setCornerRadius(dp(14));
        field.setStroke(Math.max(1, dp(1)), 0x66E0B15A);
        search.setBackground(field);
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = dp(12);
        column.addView(search, searchLp);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString();
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                hideKeyboard();
                return true;
            }
        });

        banner = new TextView(this);
        banner.setText("ပင်မမျက်နှာပြင်အဖြစ် သတ်မှတ်ရန်");
        banner.setTextColor(0xFF1A1208);
        banner.setGravity(Gravity.CENTER);
        banner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        banner.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bannerBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFFFE7A8, 0xFFE0B15A});
        bannerBg.setCornerRadius(dp(14));
        banner.setBackground(bannerBg);
        banner.setVisibility(View.GONE);
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bannerLp.topMargin = dp(10);
        column.addView(banner, bannerLp);
        banner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHomeSettings();
            }
        });

        empty = new TextView(this);
        empty.setText("အက်ပ်များ ဖွင့်နေသည်…");
        empty.setTextColor(0xFFA89878);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);

        grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(8));
        grid.setHorizontalSpacing(dp(2));
        grid.setGravity(Gravity.CENTER);
        grid.setSelector(new ColorDrawable(0x00000000));
        grid.setCacheColorHint(0);
        grid.setClipToPadding(false);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < shown.size()) launch(shown.get(position));
            }
        });
        grid.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < shown.size()) openInfo(shown.get(position).pkg);
                return true;
            }
        });

        LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        fill.topMargin = dp(8);
        column.addView(grid, fill);
        column.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        grid.setEmptyView(empty);

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(6), dp(8), dp(6), dp(8));
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(0xF0120E0B);
        dockBg.setCornerRadius(dp(22));
        dockBg.setStroke(Math.max(1, dp(1)), 0x59E0B15A);
        dock.setBackground(dockBg);
        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockLp.topMargin = dp(6);
        column.addView(dock, dockLp);

        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        lockLayer = new LockLayer(this);
        buildLock(lockLayer);
        root.addView(lockLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        settingsPanel = buildSettings();
        settingsPanel.setVisibility(View.GONE);
        root.addView(settingsPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildLock(FrameLayout layer) {
        layer.setClickable(true);
        LinearLayout timeBox = new LinearLayout(this);
        timeBox.setOrientation(LinearLayout.VERTICAL);
        timeBox.setGravity(Gravity.CENTER_HORIZONTAL);
        timeBox.setPadding(dp(20), dp(18), dp(20), 0);

        TextView sys = ghost("SYS");
        sys.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.START);
        row.addView(sys);
        timeBox.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setGravity(Gravity.CENTER);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        lockTime = clockText(52);
        lockSeconds = new TextView(this);
        lockSeconds.setTextColor(0xFFE0B15A);
        lockSeconds.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        lockSeconds.setTypeface(displayFont);
        lockSeconds.setPadding(dp(8), dp(28), 0, 0);
        clockRow.addView(lockTime);
        clockRow.addView(lockSeconds);
        timeBox.addView(clockRow);

        lockDate = new TextView(this);
        lockDate.setTextColor(0xFFA89878);
        lockDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        lockDate.setGravity(Gravity.CENTER);
        lockDate.setPadding(0, dp(6), 0, 0);
        timeBox.addView(lockDate);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.gravity = Gravity.TOP;
        layer.addView(timeBox, topLp);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.addView(wordmark(48));
        center.addView(byline());
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        centerLp.gravity = Gravity.CENTER;
        layer.addView(center, centerLp);

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setGravity(Gravity.CENTER_HORIZONTAL);
        foot.setPadding(dp(24), 0, dp(24), dp(28));
        TextView hint = new TextView(this);
        hint.setText("အပေါ်သို့ ပွတ်ဆွဲပါ");
        hint.setTextColor(0xFFA89878);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hint.setGravity(Gravity.CENTER);
        foot.addView(hint);
        TextView enter = solid("ဝင်ရန်");
        LinearLayout.LayoutParams enterLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        enterLp.topMargin = dp(12);
        enter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unlock();
            }
        });
        foot.addView(enter, enterLp);
        FrameLayout.LayoutParams footLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footLp.gravity = Gravity.BOTTOM;
        layer.addView(foot, footLp);
    }

    private View buildSettings() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xF207060A);
        scroll.setClickable(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(24), dp(20), dp(24));

        TextView mark = wordmark(36);
        box.addView(mark);
        box.addView(byline());

        TextView about = body("Cyber Gold\nLock screen + home screen\nRedmi 9A · armeabi-v7a · 32-bit\nAndroid 5.0 နှင့်အထက်");
        about.setPadding(0, dp(16), 0, dp(8));
        box.addView(about);

        lockToggle = solid(lockEnabled ? "Lock screen · ON" : "Lock screen · OFF");
        lockToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lockEnabled = !lockEnabled;
                prefs.edit().putBoolean("lock", lockEnabled).apply();
                lockToggle.setText(lockEnabled ? "Lock screen · ON" : "Lock screen · OFF");
                if (!lockEnabled) unlock();
            }
        });
        LinearLayout.LayoutParams btn = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btn.topMargin = dp(14);
        box.addView(lockToggle, btn);

        TextView home = ghost("ပင်မမျက်နှာပြင်အဖြစ် သတ်မှတ်ရန်");
        home.setGravity(Gravity.CENTER);
        home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHomeSettings();
            }
        });
        box.addView(home, btn);

        TextView help = body("Home ခလုတ်နှိပ်ပြီး G.I Launcher ကို Always ရွေးပါ။\n"
                + "Security → Autostart မှာ G.I Launcher ကို ဖွင့်ပါ။\n"
                + "Battery saver ကို No restrictions ထားပါ။\n"
                + "ဖုန်းပိတ်ပြီး ပြန်ဖွင့်တိုင်း lock screen ပေါ်ပါမယ်။ အပေါ်ဆွဲပြီး ဝင်ပါ။\n"
                + "အိုင်ကွန်ကို ကြာကြာနှိပ်ရင် app အချက်အလက် ပွင့်ပါတယ်။");
        help.setPadding(0, dp(16), 0, dp(8));
        box.addView(help);

        TextView close = ghost("ပိတ်မည်");
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSettings();
            }
        });
        box.addView(close, btn);
        scroll.addView(box);
        return scroll;
    }

    private void startLoad() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Item> items = new ArrayList<Item>();
                try {
                    Intent main = new Intent(Intent.ACTION_MAIN, null);
                    main.addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
                    String me = getPackageName();
                    for (int i = 0; i < list.size(); i++) {
                        ResolveInfo ri = list.get(i);
                        if (ri.activityInfo == null) continue;
                        String pkg = ri.activityInfo.packageName;
                        if (me.equals(pkg)) continue;
                        CharSequence label = ri.loadLabel(pm);
                        String text = label == null ? pkg : label.toString();
                        items.add(new Item(pkg, ri.activityInfo.name, text));
                    }
                    Collections.sort(items, new Comparator<Item>() {
                        @Override
                        public int compare(Item a, Item b) {
                            return a.label.compareToIgnoreCase(b.label);
                        }
                    });
                } catch (Exception ignored) {
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        acceptApps(items);
                    }
                });
            }
        }, "gi-apps").start();
    }

    private void acceptApps(List<Item> items) {
        all.clear();
        all.addAll(items);
        dockItems.clear();
        HashSet<String> taken = new HashSet<String>();
        for (int s = 0; s < DOCK.length; s++) {
            Item pick = null;
            for (int c = 0; c < DOCK[s].length && pick == null; c++) {
                pick = findPkg(items, DOCK[s][c]);
            }
            if (pick != null && taken.add(pick.pkg)) dockItems.add(pick);
        }
        for (int i = 0; i < items.size() && dockItems.size() < 4; i++) {
            if (taken.add(items.get(i).pkg)) dockItems.add(items.get(i));
        }
        applyFilter();
        bindDock();
        iconQueue.clear();
        iconQueue.addAll(items);
        handler.removeCallbacks(iconPump);
        handler.post(iconPump);
    }

    private void applyFilter() {
        shown.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        HashSet<String> dockPkgs = new HashSet<String>();
        for (int i = 0; i < dockItems.size(); i++) dockPkgs.add(dockItems.get(i).pkg);
        for (int i = 0; i < all.size(); i++) {
            Item it = all.get(i);
            if (q.length() == 0 && dockPkgs.contains(it.pkg)) continue;
            if (q.length() == 0 || it.label.toLowerCase(Locale.ROOT).contains(q)) shown.add(it);
        }
        if (empty != null) {
            empty.setText(all.isEmpty() ? "အက်ပ်များ ဖွင့်နေသည်…" : "မတွေ့ပါ");
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void bindDock() {
        dock.removeAllViews();
        for (int i = 0; i < dockItems.size(); i++) {
            final Item it = dockItems.get(i);
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, iconPx, 1f);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setTag(it.pkg);
            iv.setContentDescription(it.label);
            Bitmap b = icons.get(it.pkg);
            iv.setImageBitmap(b != null ? b : placeholder);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launch(it);
                }
            });
            iv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    openInfo(it.pkg);
                    return true;
                }
            });
            dock.addView(iv);
        }
    }

    private void paintIcons() {
        paintGroup(grid);
        paintGroup(dock);
    }

    private void paintGroup(ViewGroup group) {
        if (group == null) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ImageView) {
                applyIcon((ImageView) child);
            } else if (child instanceof ViewGroup) {
                ViewGroup nested = (ViewGroup) child;
                for (int j = 0; j < nested.getChildCount(); j++) {
                    if (nested.getChildAt(j) instanceof ImageView) {
                        applyIcon((ImageView) nested.getChildAt(j));
                    }
                }
            }
        }
    }

    private void applyIcon(ImageView iv) {
        Object tag = iv.getTag();
        if (!(tag instanceof String)) return;
        Bitmap bmp = icons.get((String) tag);
        if (bmp != null) iv.setImageBitmap(bmp);
    }

    private Item findPkg(List<Item> items, String pkg) {
        for (int i = 0; i < items.size(); i++) {
            if (pkg.equals(items.get(i).pkg)) return items.get(i);
        }
        return null;
    }

    private void launch(Item it) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(it.pkg, it.cls));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "ဖွင့်လို့မရပါ", Toast.LENGTH_SHORT).show();
        }
    }

    private void openInfo(String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, pkg, Toast.LENGTH_SHORT).show();
        }
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ignored) {
                Toast.makeText(this, "Home app ကို Settings ထဲမှာ ရွေးပါ", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refreshBanner() {
        if (banner == null) return;
        banner.setVisibility(isDefaultHome() ? View.GONE : View.VISIBLE);
    }

    private boolean isDefaultHome() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            return info != null && info.activityInfo != null
                    && getPackageName().equals(info.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    private void showLock() {
        if (!lockEnabled || lockLayer == null) return;
        closeSettings();
        hideKeyboard();
        lockLayer.animate().cancel();
        lockLayer.setVisibility(View.VISIBLE);
        lockLayer.setTranslationY(0f);
        lockLayer.setAlpha(1f);
    }

    private void unlock() {
        armed = false;
        if (lockLayer == null) return;
        final int travel = Math.max(lockLayer.getHeight(), root == null ? 0 : root.getHeight());
        lockLayer.animate().translationY(-Math.max(travel, dp(200))).setDuration(260)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!armed) lockLayer.setVisibility(View.GONE);
                    }
                }).start();
    }

    private void openSettings() {
        if (settingsPanel != null) settingsPanel.setVisibility(View.VISIBLE);
    }

    private void closeSettings() {
        if (settingsPanel != null) settingsPanel.setVisibility(View.GONE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && search != null) {
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        }
    }

    private void updateClock() {
        Calendar cal = Calendar.getInstance();
        boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        int hour = cal.get(h24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        if (!h24 && hour == 0) hour = 12;
        String hm = String.format(Locale.US, "%02d:%02d", hour, cal.get(Calendar.MINUTE));
        String sec = String.format(Locale.US, "%02d", cal.get(Calendar.SECOND));
        if (homeTime != null) homeTime.setText(hm);
        if (lockTime != null) lockTime.setText(hm);
        if (lockSeconds != null) lockSeconds.setText(sec);
        String date;
        try {
            date = new SimpleDateFormat("EEEE, d MMMM", new Locale("my", "MM")).format(new Date());
        } catch (Exception e) {
            date = new SimpleDateFormat("EEE, d MMM", Locale.US).format(new Date());
        }
        if (homeDate != null) homeDate.setText(date);
        if (lockDate != null) lockDate.setText(date);
    }

    private TextView clockText(float sp) {
        TextView tv = new TextView(this);
        tv.setText("--:--");
        tv.setTextColor(0xFFFFE7A8);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTypeface(displayFont);
        tv.setGravity(Gravity.CENTER);
        tv.setLetterSpacing(0.06f);
        return tv;
    }

    private TextView wordmark(float sp) {
        final TextView tv = new TextView(this);
        tv.setText("G.I");
        tv.setTextColor(0xFFFFE7A8);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTypeface(displayFont);
        tv.setGravity(Gravity.CENTER);
        tv.setLetterSpacing(0.2f);
        tv.post(new Runnable() {
            @Override
            public void run() {
                float h = tv.getTextSize();
                tv.getPaint().setShader(new LinearGradient(
                        0, 0, 0, h,
                        new int[]{0xFFFFF6D8, 0xFFE0B15A, 0xFF8A6420},
                        new float[]{0f, 0.45f, 1f},
                        Shader.TileMode.CLAMP));
                tv.invalidate();
            }
        });
        return tv;
    }

    private TextView byline() {
        TextView tv = new TextView(this);
        tv.setText("powered by I ko ko");
        tv.setTextColor(0xFFE0B15A);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setGravity(Gravity.CENTER);
        tv.setLetterSpacing(0.04f);
        tv.setPadding(0, dp(2), 0, 0);
        return tv;
    }

    private TextView body(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFF6EDD8);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setLineSpacing(dp(3), 1f);
        return tv;
    }

    private TextView ghost(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(0xFFFFE7A8);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setTypeface(displayFont);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setMinHeight(dp(40));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xCC120E0B);
        gd.setCornerRadius(dp(20));
        gd.setStroke(Math.max(1, dp(1)), 0x73E0B15A);
        b.setBackground(gd);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private TextView solid(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(0xFF1A1208);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(18), dp(12), dp(18), dp(12));
        b.setMinHeight(dp(48));
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFE7A8, 0xFFE0B15A, 0xFF8A6420});
        gd.setCornerRadius(dp(24));
        b.setBackground(gd);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            ImageView iv;
            TextView tv;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                iv = (ImageView) row.getChildAt(0);
                tv = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(HomeActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                iv = new ImageView(HomeActivity.this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setLayoutParams(new LinearLayout.LayoutParams(iconPx, iconPx));
                tv = new TextView(HomeActivity.this);
                tv.setTextColor(0xFFF6EDD8);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                tv.setGravity(Gravity.CENTER_HORIZONTAL);
                tv.setMaxLines(1);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setPadding(dp(2), dp(4), dp(2), 0);
                row.addView(iv);
                row.addView(tv);
            }
            Item it = shown.get(position);
            tv.setText(it.label);
            iv.setTag(it.pkg);
            iv.setContentDescription(it.label);
            Bitmap bmp = icons.get(it.pkg);
            iv.setImageBitmap(bmp != null ? bmp : placeholder);
            return row;
        }
    }

    private final class LockLayer extends FrameLayout {
        private float downX;
        private float downY;
        private boolean dragging;
        private final int slop;

        LockLayer(Context context) {
            super(context);
            slop = ViewConfiguration.get(context).getScaledTouchSlop();
            setClickable(true);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    dragging = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dy = e.getRawY() - downY;
                    float dx = e.getRawX() - downX;
                    if (-dy > slop && -dy > Math.abs(dx)) {
                        dragging = true;
                        setTranslationY(Math.min(0f, dy));
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    setTranslationY(Math.min(0f, e.getRawY() - downY));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (getTranslationY() < -dp(90)) unlock();
                    else animate().translationY(0f).setDuration(180).start();
                    dragging = false;
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }
    }

    private static final class Item {
        final String pkg;
        final String cls;
        final String label;

        Item(String pkg, String cls, String label) {
            this.pkg = pkg;
            this.cls = cls;
            this.label = label;
        }
    }
}
