package org.telegram.ui;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.UUID;

public class MentholVpnSubscriptionStore {
    private static final String PREFS = "menthol_vpn";
    private static final String KEY_SUBSCRIPTIONS = "subscriptions";
    private static final String KEY_SELECTED_CONFIG = "selectedConfig";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_EXTERNAL_VPN_PREFERRED = "externalVpnPreferred";

    public static class Subscription {
        public String id;
        public String name;
        public String url;
        public long lastUpdated;
        public ArrayList<MentholVpnConfigParser.ParsedConfig> configs = new ArrayList<>();
    }

    public static ArrayList<Subscription> load() {
        ArrayList<Subscription> result = new ArrayList<>();
        try {
            String raw = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SUBSCRIPTIONS, "[]");
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                Subscription subscription = new Subscription();
                subscription.id = object.optString("id");
                subscription.name = object.optString("name");
                subscription.url = object.optString("url");
                subscription.lastUpdated = object.optLong("lastUpdated");
                JSONArray configs = object.optJSONArray("configs");
                if (configs != null) {
                    for (int j = 0; j < configs.length(); j++) {
                        JSONObject item = configs.getJSONObject(j);
                        MentholVpnConfigParser.ParsedConfig config = new MentholVpnConfigParser.ParsedConfig();
                        config.type = item.optString("type");
                        config.name = item.optString("name");
                        config.host = item.optString("host");
                        config.port = item.optInt("port");
                        config.pingMs = item.optInt("pingMs");
                        config.raw = item.optString("raw");
                        subscription.configs.add(config);
                    }
                }
                result.add(subscription);
            }
        } catch (Exception ignore) {
        }
        return result;
    }

    public static Subscription addOrUpdate(String name, String url, ArrayList<MentholVpnConfigParser.ParsedConfig> configs) {
        ArrayList<Subscription> subscriptions = load();
        Subscription target = null;
        for (Subscription subscription : subscriptions) {
            if (subscription.url.equals(url)) {
                target = subscription;
                break;
            }
        }
        if (target == null) {
            target = new Subscription();
            target.id = UUID.randomUUID().toString();
            subscriptions.add(target);
        }
        target.name = name == null || name.trim().isEmpty() ? url : name.trim();
        target.url = url;
        target.lastUpdated = System.currentTimeMillis();
        target.configs = configs == null ? new ArrayList<>() : configs;
        save(subscriptions);
        return target;
    }

    public static void remove(String id) {
        ArrayList<Subscription> subscriptions = load();
        for (int i = 0; i < subscriptions.size(); i++) {
            if (subscriptions.get(i).id.equals(id)) {
                subscriptions.remove(i);
                break;
            }
        }
        save(subscriptions);
    }

    public static void removeConfig(String subscriptionId, String raw) {
        ArrayList<Subscription> subscriptions = load();
        for (int i = 0; i < subscriptions.size(); i++) {
            Subscription subscription = subscriptions.get(i);
            if (!subscription.id.equals(subscriptionId)) {
                continue;
            }
            for (int j = 0; j < subscription.configs.size(); j++) {
                if (raw != null && raw.equals(subscription.configs.get(j).raw)) {
                    subscription.configs.remove(j);
                    break;
                }
            }
            if (subscription.configs.isEmpty() || subscription.url != null && subscription.url.startsWith("manual://")) {
                subscriptions.remove(i);
            }
            break;
        }
        save(subscriptions);
    }

    public static void updateConfig(String subscriptionId, String oldRaw, MentholVpnConfigParser.ParsedConfig newConfig) {
        ArrayList<Subscription> subscriptions = load();
        for (Subscription subscription : subscriptions) {
            if (!subscription.id.equals(subscriptionId)) {
                continue;
            }
            for (int i = 0; i < subscription.configs.size(); i++) {
                if (oldRaw != null && oldRaw.equals(subscription.configs.get(i).raw)) {
                    subscription.configs.set(i, newConfig);
                    save(subscriptions);
                    return;
                }
            }
        }
    }

    public static void updateSubscriptionName(String subscriptionId, String name) {
        ArrayList<Subscription> subscriptions = load();
        for (Subscription subscription : subscriptions) {
            if (subscription.id.equals(subscriptionId)) {
                subscription.name = name == null || name.trim().isEmpty() ? subscription.url : name.trim();
                break;
            }
        }
        save(subscriptions);
    }

    public static void saveAll(ArrayList<Subscription> subscriptions) {
        save(subscriptions);
    }

    public static String getSelectedConfigRaw() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SELECTED_CONFIG, null);
    }

    public static void setSelectedConfigRaw(String raw) {
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SELECTED_CONFIG, raw).apply();
    }

    public static boolean isRunning() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RUNNING, false);
    }

    public static void setRunning(boolean running) {
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, running).apply();
    }

    public static boolean isExternalVpnPreferred() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_EXTERNAL_VPN_PREFERRED, false);
    }

    public static void setExternalVpnPreferred(boolean preferred) {
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_EXTERNAL_VPN_PREFERRED, preferred).apply();
    }

    private static void save(ArrayList<Subscription> subscriptions) {
        JSONArray array = new JSONArray();
        try {
            for (Subscription subscription : subscriptions) {
                JSONObject object = new JSONObject();
                object.put("id", subscription.id);
                object.put("name", subscription.name);
                object.put("url", subscription.url);
                object.put("lastUpdated", subscription.lastUpdated);
                JSONArray configs = new JSONArray();
                for (MentholVpnConfigParser.ParsedConfig config : subscription.configs) {
                    JSONObject item = new JSONObject();
                    item.put("type", config.type);
                    item.put("name", config.name);
                    item.put("host", config.host);
                    item.put("port", config.port);
                    item.put("pingMs", config.pingMs);
                    item.put("raw", config.raw);
                    configs.put(item);
                }
                object.put("configs", configs);
                array.put(object);
            }
        } catch (Exception ignore) {
        }
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SUBSCRIPTIONS, array.toString()).apply();
    }
}
