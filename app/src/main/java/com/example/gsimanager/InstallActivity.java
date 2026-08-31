package com.example.gsimanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;

public class InstallActivity extends Activity {

    private static final int REQ_ZIP = 2001;
    private static final int REQ_NOTIF = 2002;
    private static final long GB = 1024L * 1024L * 1024L;

    private TextView size8;
    private TextView size16;
    private TextView size32;
    private TextView size64;
    private TextView sizeCustom;
    private LinearLayout customRow;
    private EditText customGb;
    private TextView zipName;
    private CheckBox chkWipe;
    private TextView btnInstall;
    private TextView installLog;
    private TextView btnRebootNow;

    private Uri zipUri;
    private String zipDisplayName;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean installing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_install);

        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        size8 = findViewById(R.id.size8);
        size16 = findViewById(R.id.size16);
        size32 = findViewById(R.id.size32);
        size64 = findViewById(R.id.size64);
        sizeCustom = findViewById(R.id.sizeCustom);
        customRow = findViewById(R.id.customRow);
        customGb = findViewById(R.id.customGb);
        zipName = findViewById(R.id.zipName);
        chkWipe = findViewById(R.id.chkWipe);
        btnInstall = findViewById(R.id.btnInstall);
        installLog = findViewById(R.id.installLog);
        btnRebootNow = findViewById(R.id.btnRebootNow);

        TextView[] sizes = {size8, size16, size32, size64, sizeCustom};
        for (TextView b : sizes) {
            b.setOnClickListener(v -> selectSize(b));
        }
        selectSize(size8);

        findViewById(R.id.btnBrowse).setOnClickListener(v -> pickZip());
        btnInstall.setOnClickListener(v -> startInstall());
        btnRebootNow.setOnClickListener(v -> confirmReboot());
    }

    private void selectSize(TextView selected) {
        TextView[] sizes = {size8, size16, size32, size64, sizeCustom};
        for (TextView b : sizes) {
            b.setSelected(b == selected);
        }
        customRow.setVisibility(selected == sizeCustom ? View.VISIBLE : View.GONE);
    }

    private void pickZip() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_ZIP);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ZIP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            zipUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(
                        zipUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            zipDisplayName = queryDisplayName(zipUri);
            zipName.setText(zipDisplayName);
            zipName.setTextColor(0xFF1F2937);
            installLog.setText("等待开始…");
        }
    }

    private String queryDisplayName(Uri uri) {
        try {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = c.getString(idx);
                    c.close();
                    return name;
                }
                c.close();
            }
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment();
    }

    private String toRealPath(Uri uri) {
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            return uri.getPath();
        }
        if (!"content".equals(scheme)) {
            return null;
        }
        if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            java.util.List<String> seg = uri.getPathSegments();
            if (seg.size() >= 2 && "document".equals(seg.get(0))) {
                String docId = seg.get(1);
                String[] parts = docId.split(":", 2);
                if (parts.length == 2) {
                    String root = parts[0];
                    String rest = parts[1];
                    if ("primary".equals(root) || "home".equals(root)) {
                        return "/storage/emulated/0/" + rest;
                    }
                    return "/storage/" + root + "/" + rest;
                }
            }
        }
        return null;
    }

    private long querySize(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !c.isNull(idx)) {
                    return c.getLong(idx);
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private long selectedUserdataBytes() {
        long gb;
        if (size8.isSelected()) {
            gb = 8;
        } else if (size16.isSelected()) {
            gb = 16;
        } else if (size32.isSelected()) {
            gb = 32;
        } else if (size64.isSelected()) {
            gb = 64;
        } else {
            String text = customGb.getText().toString().trim();
            if (text.isEmpty()) {
                return -1;
            }
            try {
                gb = Long.parseLong(text);
            } catch (NumberFormatException e) {
                return -1;
            }
            if (gb < 1 || gb > 1024) {
                return -1;
            }
        }
        return gb * GB;
    }

    private void startInstall() {
        if (installing) {
            return;
        }
        if (zipUri == null) {
            Toast.makeText(this, "请先选择 GSI 安装包（zip）", Toast.LENGTH_SHORT).show();
            return;
        }
        long userdata = selectedUserdataBytes();
        if (userdata < 0) {
            Toast.makeText(this, "请输入有效的自定义容量（1-1024 GB）", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Shell.isRooted()) {
            Toast.makeText(this, "需要 Root 权限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        installing = true;
        setInstallEnabled(false);
        btnRebootNow.setVisibility(View.GONE);
        installLog.setText("正在准备安装…");
        new Thread(() -> doInstall(userdata)).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF && installing) {
            installLog.setText("正在准备安装…");
        }
    }

    private void doInstall(long userdataBytes) {
        try {
            long size = querySize(getContentResolver(), zipUri);
            if (size <= 0) {
                appendLog("错误：无法获取文件大小");
                return;
            }

            if (chkWipe.isChecked()) {
                appendLog("清除旧 GSI userdata…");
                Shell.exec(Shell.GSI_TOOL + " wipe-data", 120);
            }

            appendLog("重置系统 DSU 服务…");
            Shell.exec("am force-stop com.android.dynsystem", 15);
            Shell.exec("setprop persist.sys.fflag.override.settings_dynamic_system true", 15);

            appendLog(String.format("文件大小: %.2f GB", size / (double) GB));
            appendLog(String.format("userdata 容量: %.2f GB", userdataBytes / (double) GB));

            String installUri;
            String realPath = toRealPath(zipUri);
            if (realPath != null) {
                appendLog("直接定位所选安装包: " + realPath);
                installUri = Uri.fromFile(new File(realPath)).toString();
            } else {
                appendLog("所选文件无法直接定位（非本地存储），回退拷贝到 /data/local/tmp…");
                final String dst = "/data/local/tmp/gsi_install.zip";
                appendLog("拷贝安装包到 " + dst + "，请耐心等待…");
                InputStream in = getContentResolver().openInputStream(zipUri);
                if (in == null) {
                    appendLog("错误：无法打开安装包");
                    return;
                }
                String copyOut = Shell.execWrite("cat > " + dst + " && chmod 644 " + dst, in, 3600);
                if (copyOut == null) {
                    appendLog("错误：拷贝安装包失败（超时或空间不足）");
                    appendLog("请确认 /data 分区剩余空间足够，或改用系统文件选择器选择 zip");
                    return;
                }
                appendLog("拷贝完成");
                installUri = "file://" + dst;
            }

            String cmd = "am start-activity "
                    + "-n com.android.dynsystem/com.android.dynsystem.VerificationActivity "
                    + "-a android.os.image.action.START_INSTALL "
                    + "-d '" + installUri + "' "
                    + "--el KEY_USERDATA_SIZE " + userdataBytes + " "
                    + "--el KEY_SYSTEM_SIZE " + size;
            appendLog("$ " + cmd);

            String out = Shell.exec(cmd, 60);
            if (out == null) {
                appendLog("启动命令无响应，请查看系统界面是否出现 DSU 安装页");
                handler.post(() -> btnRebootNow.setVisibility(View.VISIBLE));
                return;
            }
            if (out.contains("Error") || out.contains("Exception") || out.contains("denied")) {
                appendLog(out);
                appendLog("启动系统 DSU 失败，请确认设备已安装 com.android.dynsystem");
                return;
            }
            appendLog(out);
            appendLog("已交由系统 DSU 安装，进度见系统界面与通知。");
            appendLog("安装完成后点击下方按钮重启进入 GSI。");
            handler.post(() -> btnRebootNow.setVisibility(View.VISIBLE));
        } catch (Exception e) {
            appendLog("异常: " + e.getMessage());
        } finally {
            installing = false;
            setInstallEnabled(true);
        }
    }

    private void appendLog(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        handler.post(() -> {
            String prev = installLog.getText().toString();
            if (prev.equals("等待开始…") || prev.isEmpty()) {
                installLog.setText(line);
            } else {
                String text = prev + "\n" + line;
                if (text.length() > 4000) {
                    text = text.substring(text.length() - 4000);
                }
                installLog.setText(text);
            }
            View parent = (View) installLog.getParent();
            if (parent instanceof ScrollView) {
                ((ScrollView) parent.getParent()).fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void setInstallEnabled(boolean enabled) {
        handler.post(() -> {
            btnInstall.setEnabled(enabled);
            btnInstall.setAlpha(enabled ? 1f : 0.45f);
        });
    }

    private void confirmReboot() {
        new AlertDialog.Builder(this)
                .setTitle("重启到 DSU")
                .setMessage("设备将立即重启。若 GSI 已安装并启用，将启动进入 GSI 系统。确定继续？")
                .setPositiveButton("重启", (d, w) -> new Thread(() -> Shell.exec("reboot", 10)).start())
                .setNegativeButton("取消", null)
                .show();
    }
}
