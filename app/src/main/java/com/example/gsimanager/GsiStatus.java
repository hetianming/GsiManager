package com.example.gsimanager;

import java.util.ArrayList;
import java.util.List;

public class GsiStatus {

    public boolean running;
    public boolean installed;
    public boolean enabled;
    public boolean disabled;
    public boolean normal;
    public boolean installInProgress;
    public final List<String> slots = new ArrayList<>();
    public final List<String> images = new ArrayList<>();
    public String avbSha1 = "";
    public String raw = "";

    public static GsiStatus parse(String output) {
        GsiStatus s = new GsiStatus();
        if (output == null) {
            return s;
        }
        s.raw = output;
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.equals("running")) {
                s.running = true;
            } else if (t.equals("installed")) {
                s.installed = true;
            } else if (t.equals("enabled")) {
                s.enabled = true;
            } else if (t.equals("disabled")) {
                s.disabled = true;
            } else if (t.equals("normal")) {
                s.normal = true;
            } else if (t.startsWith("[")) {
                s.slots.add(t);
            } else if (t.startsWith("installed:")) {
                s.images.add(t.substring("installed:".length()).trim());
            } else if (t.startsWith("AVB public key")) {
                int idx = t.indexOf(':');
                if (idx >= 0) {
                    s.avbSha1 = t.substring(idx + 1).trim();
                }
            } else if (t.toLowerCase().contains("in progress")) {
                s.installInProgress = true;
            }
        }
        return s;
    }

    public String describe() {
        if (running) {
            return "正在运行 GSI";
        }
        if (installInProgress) {
            return "GSI 安装进行中";
        }
        if (installed) {
            if (enabled) {
                return "已安装 GSI（已启用）";
            }
            if (disabled) {
                return "已安装 GSI（已禁用）";
            }
            return "已安装 GSI";
        }
        if (normal) {
            return "未安装 GSI";
        }
        if (raw == null || raw.isEmpty()) {
            return "无法连接 gsid 服务";
        }
        return "状态未知";
    }

    public String detail() {
        StringBuilder sb = new StringBuilder();
        if (running) {
            sb.append("● 运行中\n");
        }
        if (installed) {
            sb.append("● 已安装\n");
        }
        if (enabled) {
            sb.append("● 已启用\n");
        }
        if (disabled) {
            sb.append("● 已禁用\n");
        }
        if (normal) {
            sb.append("● 正常（未安装）\n");
        }
        for (String slot : slots) {
            sb.append(slot).append('\n');
        }
        for (String img : images) {
            sb.append("镜像: ").append(img).append('\n');
        }
        if (!avbSha1.isEmpty()) {
            sb.append("AVB 公钥: ").append(avbSha1).append('\n');
        }
        return sb.toString().trim();
    }
}
