package org.telegram.ui;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MentholVpnConfigParser {
    private static final Pattern URI_PATTERN = Pattern.compile("(?i)(vmess|vless|trojan|ss)://\\S+");

    public static class ParsedConfig {
        public String type;
        public String name;
        public String host;
        public int port;
        public int pingMs;
        public String raw;
    }

    public static ArrayList<ParsedConfig> parseSubscription(String body) {
        ArrayList<ParsedConfig> result = new ArrayList<>();
        for (String decoded : decodeBase64Variants(body)) {
            if (!TextUtils.isEmpty(decoded)) {
                for (ParsedConfig config : parseLoose(decoded)) {
                    if (!containsRaw(result, config.raw)) {
                        result.add(config);
                    }
                }
            }
        }
        if (result.isEmpty()) {
            result = parseLoose(body);
        }
        return result;
    }

    private static ArrayList<ParsedConfig> parseLoose(String text) {
        ArrayList<ParsedConfig> result = parseLines(text);
        if (text == null) {
            return result;
        }
        Matcher matcher = URI_PATTERN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group();
            int end = raw.length();
            while (end > 0 && ",;\")]}".indexOf(raw.charAt(end - 1)) >= 0) {
                end--;
            }
            ParsedConfig config = parseConfig(raw.substring(0, end));
            if (config != null && !containsRaw(result, config.raw)) {
                result.add(config);
            }
        }
        if (result.isEmpty() && (text.trim().startsWith("[") || text.trim().startsWith("{"))) {
            ParsedConfig config = parseConfig(text.trim());
            if (config != null) {
                result.add(config);
            }
        }
        return result;
    }

    private static ArrayList<ParsedConfig> parseLines(String text) {
        ArrayList<ParsedConfig> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        String[] lines = text.split("[\\r\\n]+");
        for (String line : lines) {
            ParsedConfig config = parseConfig(line.trim());
            if (config != null && !containsRaw(result, config.raw)) {
                result.add(config);
            }
        }
        return result;
    }

    public static ParsedConfig parseConfig(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        if (raw.startsWith("vmess://")) {
            return parseVmess(raw);
        } else if (raw.startsWith("vless://")) {
            return parseUriConfig(raw, "VLESS");
        } else if (raw.startsWith("trojan://")) {
            return parseUriConfig(raw, "Trojan");
        } else if (raw.startsWith("ss://")) {
            return parseUriConfig(raw, "Shadowsocks");
        } else if (raw.startsWith("{") || raw.startsWith("[")) {
            return parseJsonConfig(raw);
        }
        return null;
    }

    public static ArrayList<ParsedConfig> parseConfigText(String text) {
        ArrayList<ParsedConfig> result = parseSubscription(text);
        if (result.isEmpty()) {
            ParsedConfig config = parseConfig(text == null ? null : text.trim());
            if (config != null) {
                result.add(config);
            }
        }
        return result;
    }

    private static ParsedConfig parseVmess(String raw) {
        ParsedConfig config = new ParsedConfig();
        config.type = "VMess";
        config.raw = raw;
        try {
            String payload = raw.substring("vmess://".length());
            JSONObject object = new JSONObject(decodeBase64(payload));
            config.name = object.optString("ps", "VMess");
            config.host = object.optString("add", "");
            config.port = parseInt(object.optString("port"), 0);
        } catch (Exception ignore) {
            config.name = "VMess";
            config.host = "";
        }
        return config;
    }

    private static ParsedConfig parseUriConfig(String raw, String type) {
        ParsedConfig config = new ParsedConfig();
        config.type = type;
        config.raw = raw;
        try {
            Uri uri = Uri.parse(raw);
            config.host = uri.getHost() == null ? "" : uri.getHost();
            config.port = uri.getPort();
            String fragment = uri.getFragment();
            config.name = TextUtils.isEmpty(fragment) ? type : URLDecoder.decode(fragment, "UTF-8");
        } catch (Exception ignore) {
            config.name = type;
            config.host = "";
        }
        return config;
    }

    private static ParsedConfig parseJsonConfig(String raw) {
        ParsedConfig config = new ParsedConfig();
        config.type = "JSON";
        config.raw = raw;
        config.name = "Custom JSON";
        config.host = "";
        config.port = 0;
        try {
            JSONObject object = new JSONObject(raw);
            config.name = object.optString("remarks", config.name);
            org.json.JSONArray outbounds = object.optJSONArray("outbounds");
            if (outbounds != null) {
                for (int i = 0; i < outbounds.length(); i++) {
                    JSONObject outbound = outbounds.optJSONObject(i);
                    if (outbound == null || !("proxy".equals(outbound.optString("tag")) || i == 0)) {
                        continue;
                    }
                    config.type = outbound.optString("protocol", "JSON");
                    JSONObject settings = outbound.optJSONObject("settings");
                    if (settings != null) {
                        config.host = settings.optString("address", config.host);
                        config.port = settings.optInt("port", config.port);
                    }
                    break;
                }
            }
        } catch (Exception ignore) {
        }
        return config;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static ArrayList<String> decodeBase64Variants(String text) {
        ArrayList<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        String normalized = text.trim().replaceAll("\\s+", "");
        decodeBase64Into(result, normalized, Base64.DEFAULT);
        decodeBase64Into(result, normalized, Base64.URL_SAFE | Base64.NO_WRAP);
        decodeBase64Into(result, normalized.replace('-', '+').replace('_', '/'), Base64.DEFAULT);
        return result;
    }

    private static String decodeBase64(String text) {
        ArrayList<String> variants = decodeBase64Variants(text);
        return variants.isEmpty() ? null : variants.get(0);
    }

    private static void decodeBase64Into(ArrayList<String> result, String normalized, int flags) {
        try {
            int padding = normalized.length() % 4;
            if (padding > 0) {
                normalized += "====".substring(padding);
            }
            String decoded = new String(Base64.decode(normalized, flags), StandardCharsets.UTF_8);
            if (!TextUtils.isEmpty(decoded) && !result.contains(decoded)) {
                result.add(decoded);
            }
        } catch (Exception ignore) {
        }
    }

    private static boolean containsRaw(ArrayList<ParsedConfig> configs, String raw) {
        for (ParsedConfig config : configs) {
            if (raw != null && raw.equals(config.raw)) {
                return true;
            }
        }
        return false;
    }
}
