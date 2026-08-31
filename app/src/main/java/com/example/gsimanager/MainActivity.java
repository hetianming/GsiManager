package com.example.gsimanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

public class MainActivity extends Activity {

    private static final int REQ_IMAGE = 1001;

    private static final String PREF_NAME = "gsi_prefs";
    private static final String KEY_BG_TYPE = "bg_type";
    private static final String KEY_BG_COLOR = "bg_color";
    private static final String KEY_BG_IMAGE = "bg_image_uri";
    private static final String TYPE_COLOR = "color";
    private static final String TYPE_IMAGE = "image";

    private final int[] palette = {
            0xFF151F33, 0xFF1E3A8A, 0xFF312E81, 0xFF6D28D9, 0xFF0F766E,
            0xFF065F46, 0xFF0E7490, 0xFF92400E, 0xFF7F1D1D, 0xFF111827
    };

    private FrameLayout logoCard;
    private View rootDot;
    private TextView rootStatus;
    private TextView gsiStatus;
    private LinearLayout functionList;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View btnInstall;
    private View btnReboot;
    private View btnUninstall;
    private View btnInfo;
    private View btnMainInstall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        logoCard = findViewById(R.id.logoCard);
        rootDot = findViewById(R.id.rootDot);
        rootStatus = findViewById(R.id.rootStatus);
        gsiStatus = findViewById(R.id.gsiStatus);
        functionList = findViewById(R.id.functionList);
        btnMainInstall = findViewById(R.id.btnMainInstall);

        applySavedBackground();

        findViewById(R.id.btnMenu).setOnClickListener(v -> showCardMenu(v));
        findViewById(R.id.btnSettings).setOnClickListener(v -> showAbout());
        btnMainInstall.setOnClickListener(v -> startActivity(new Intent(this, InstallActivity.class)));

        buildButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildButtons() {
        btnInstall = makeFunctionCard(R.drawable.ic_wrench, "修复环境", "修复安装失败问题",
                v -> confirmFixEnvironment());
        btnReboot = makeFunctionCard(R.drawable.ic_refresh, "重启到 DSU", "重启进入已安装的 GSI 系统",
                v -> confirmReboot());
        btnUninstall = makeFunctionCard(R.drawable.ic_delete, "撤销已安装", "移除当前 GSI 及其数据，回到原系统",
                v -> confirmWipe());
        btnInfo = makeFunctionCard(R.drawable.ic_info, "GSI 信息", "查看当前 GSI 状态与镜像详情",
                v -> showInfo());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        functionList.addView(btnInstall, lp);
        functionList.addView(btnReboot, lp);
        functionList.addView(btnUninstall, lp);
        functionList.addView(btnInfo, lp);
    }

    private View makeFunctionCard(int iconRes, String title, String sub, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(listener);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0xFFE5E7EB);
        card.setBackground(bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.CENTER_VERTICAL);

        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextColor(0xFF1F2937);
        t1.setTextSize(16);
        t1.setTypeface(Typeface.DEFAULT_BOLD);

        TextView t2 = new TextView(this);
        t2.setText(sub);
        t2.setTextColor(0xFF6B7280);
        t2.setTextSize(12);
        t2.setMaxLines(1);
        t2.setEllipsize(android.text.TextUtils.TruncateAt.END);

        textCol.addView(t1);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = dp(3);
        textCol.addView(t2, lp2);

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextColor(0xFF9CA3AF);
        chevron.setTextSize(26);

        card.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp3.leftMargin = dp(14);
        card.addView(textCol, lp3);
        card.addView(chevron);

        return card;
    }

    private void refreshStatus() {
        gsiStatus.setText("GSI 状态检测中…");
        setButtonsEnabled(false);
        new Thread(() -> {
            boolean rooted = Shell.isRooted();
            GsiStatus status = rooted
                    ? GsiStatus.parse(queryStatusOutput())
                    : null;
            handler.post(() -> applyStatus(rooted, status));
        }).start();
    }

    private String queryStatusOutput() {
        String out = Shell.exec(Shell.GSI_TOOL + " status", 15);
        if (out == null || out.trim().isEmpty() || out.contains("Unrecognized command")) {
            String alt = Shell.exec(Shell.GSI_TOOL + " getstatus", 15);
            if (alt != null && !alt.trim().isEmpty() && !alt.contains("Unrecognized command")) {
                out = alt;
            }
        }
        return out;
    }

    private void applyStatus(boolean rooted, GsiStatus status) {
        if (rooted) {
            rootDot.setBackground(rounded(0xFF3B82F6, dp(5)));
            rootStatus.setText("已获取 Root 权限");
            rootStatus.setTextColor(0xFFFFEB3B);
            rootStatus.setShadowLayer(3f, 1f, 1f, 0xFF000000);
            setButtonsEnabled(true);
        } else {
            rootDot.setBackground(rounded(0xFFEF4444, dp(5)));
            rootStatus.setText("未检测到 Root 权限");
            rootStatus.setTextColor(0xFFDC2626);
            rootStatus.setShadowLayer(3f, 1f, 1f, 0xFF000000);
            setButtonsEnabled(false);
        }

        if (!rooted) {
            gsiStatus.setText("需要 Root 权限");
            gsiStatus.setTextColor(0xFFDC2626);
            gsiStatus.setShadowLayer(0, 0, 0, 0);
            return;
        }
        if (status == null) {
            gsiStatus.setText("GSI 状态未知");
            gsiStatus.setTextColor(0xFFDC2626);
            gsiStatus.setShadowLayer(0, 0, 0, 0);
            return;
        }
        gsiStatus.setText(status.describe());
        if (status.running) {
            gsiStatus.setTextColor(0xFF16A34A);
            gsiStatus.setShadowLayer(0, 0, 0, 0);
        } else if (status.installed) {
            gsiStatus.setTextColor(0xFFFFFFFF);
            gsiStatus.setShadowLayer(3f, 1f, 1f, 0xFF000000);
        } else if (status.normal) {
            gsiStatus.setTextColor(0xFFFFFFFF);
            gsiStatus.setShadowLayer(3f, 1f, 1f, 0xFF000000);
        } else {
            gsiStatus.setTextColor(0xFFDC2626);
            gsiStatus.setShadowLayer(0, 0, 0, 0);
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        View[] views = {btnInstall, btnReboot, btnUninstall, btnInfo, btnMainInstall};
        for (View v : views) {
            if (v != null) {
                v.setEnabled(enabled);
                v.setAlpha(enabled ? 1f : 0.35f);
            }
        }
    }

    private void confirmFixEnvironment() {
        new AlertDialog.Builder(this)
                .setTitle("修复环境")
                .setMessage("将删除 /metadata/gsi/dsu/dsu 与 /data/gsi/dsu/dsu 目录，清除旧的 DSU 安装残留，用于修复安装失败问题。确定继续？")
                .setPositiveButton("修复", (d, w) -> new Thread(() -> {
                    String out = Shell.exec("rm -rf /metadata/gsi/dsu/dsu /data/gsi/dsu/dsu", 30);
                    handler.post(() -> {
                        Toast.makeText(this, out == null ? "执行失败（无输出）" : "修复完成", Toast.LENGTH_LONG).show();
                        refreshStatus();
                    });
                }).start())
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmReboot() {
        new AlertDialog.Builder(this)
                .setTitle("重启到 DSU")
                .setMessage("设备将立即重启。若已安装并启用 GSI，将启动进入 GSI 系统。确定继续？")
                .setPositiveButton("重启", (d, w) -> {
        new Thread(() -> {
            GsiStatus st = GsiStatus.parse(queryStatusOutput());
            if (st.installed && st.disabled) {
                Shell.exec(Shell.GSI_TOOL + " enable", 30);
            }
            Shell.exec("reboot", 10);
        }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmWipe() {
        new AlertDialog.Builder(this)
                .setTitle("撤销 GSI")
                .setMessage("将完全移除已安装的 GSI 及其数据（gsi_tool wipe），设备将回到原系统。确定继续？")
                .setPositiveButton("撤销", (d, w) -> {
                    setButtonsEnabled(false);
                    new Thread(() -> {
                        String out = Shell.exec(Shell.GSI_TOOL + " wipe", 120);
                        handler.post(() -> {
                            Toast.makeText(this,
                                    out == null ? "执行失败（无输出）" : out,
                                    Toast.LENGTH_LONG).show();
                            refreshStatus();
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCardMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("换背景色");
        popup.getMenu().add("换背景图");
        popup.getMenu().add("刷新");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("换背景色".equals(title)) {
                showColorDialog();
            } else if ("换背景图".equals(title)) {
                pickImage();
            } else {
                refreshStatus();
            }
            return true;
        });
        popup.show();
    }

    private void showAbout() {
        String version = "1.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        new AlertDialog.Builder(this)
                .setTitle("关于")
                .setIcon(R.drawable.ic_logo)
                .setMessage(getString(R.string.app_name)
                        + " v" + version + "\n"
                        + "基于系统 DSU 的 GSI 管理工具\n\n"
                        + "作者：酷安@菜鸟_曾经的天明")
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showInfo() {
        AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle("已安装 GSI 信息")
                .setMessage("读取中…")
                .setCancelable(false)
                .show();
        new Thread(() -> {
            String out = queryStatusOutput();
            GsiStatus st = GsiStatus.parse(out);
            String parsed = st.detail();
            String raw = out == null ? "无法执行 gsi_tool status" : out;
            handler.post(() -> {
                loading.dismiss();
                AlertDialog d = new AlertDialog.Builder(this)
                        .setTitle("已安装 GSI 信息")
                        .setMessage(parsed.isEmpty() ? raw : parsed + "\n\n—— 原始输出 ——\n" + raw)
                        .setPositiveButton("关闭", null)
                        .show();
                TextView msg = d.findViewById(android.R.id.message);
                if (msg != null) {
                    msg.setTextColor(0xFF1F2937);
                }
            });
        }).start();
    }

    private void showColorDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择背景色")
                .setView(null)
                .create();
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setPadding(dp(20), dp(20), dp(20), dp(20));
        for (int color : palette) {
            View swatch = new View(this);
            swatch.setBackground(rounded(color, dp(10)));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(48);
            lp.height = dp(48);
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            swatch.setOnClickListener(v -> {
                prefs.edit().putString(KEY_BG_TYPE, TYPE_COLOR)
                        .putInt(KEY_BG_COLOR, color)
                        .putString(KEY_BG_IMAGE, null)
                        .apply();
                applySavedBackground();
                dialog.dismiss();
            });
            grid.addView(swatch, lp);
        }
        dialog.setView(grid);
        dialog.show();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            prefs.edit().putString(KEY_BG_TYPE, TYPE_IMAGE)
                    .putString(KEY_BG_IMAGE, uri.toString())
                    .apply();
            applySavedBackground();
        }
    }

    private void applySavedBackground() {
        String type = prefs.getString(KEY_BG_TYPE, "");
        if (TYPE_COLOR.equals(type)) {
            int color = prefs.getInt(KEY_BG_COLOR, 0xFF151F33);
            logoCard.setBackground(rounded(color, dp(24)));
            return;
        }
        if (TYPE_IMAGE.equals(type)) {
            String uriStr = prefs.getString(KEY_BG_IMAGE, null);
            if (uriStr != null) {
                try {
                    Bitmap bmp = loadSampled(Uri.parse(uriStr), 1600);
                    if (bmp != null) {
                        logoCard.setBackground(new RoundedImageDrawable(bmp, dp(24)));
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        logoCard.setBackground(getDrawable(R.drawable.bg_card_dark));
    }

    private Bitmap loadSampled(Uri uri, int maxDim) {
        ContentResolver cr = getContentResolver();
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream is = cr.openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, opts);
        } catch (Exception ignored) {
            return null;
        }
        int sample = 1;
        int dim = Math.max(opts.outWidth, opts.outHeight);
        while (dim / (sample * 2) >= maxDim) {
            sample *= 2;
        }
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sample;
        try (InputStream is = cr.openInputStream(uri)) {
            return BitmapFactory.decodeStream(is, null, opts);
        } catch (Exception ignored) {
            return null;
        }
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
