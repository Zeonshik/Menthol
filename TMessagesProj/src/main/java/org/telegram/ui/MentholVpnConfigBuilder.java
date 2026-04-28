package org.telegram.ui;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class MentholVpnConfigBuilder {
    public static final int SOCKS_PORT = 12080;

    public static String build(String raw) throws Exception {
        if (raw == null) {
            throw new IllegalArgumentException("empty config");
        }
        raw = raw.trim();
        if (raw.startsWith("{")) {
            JSONObject json = new JSONObject(raw);
            ensureSocksInbound(json);
            return json.toString();
        }
        JSONObject outbound;
        if (raw.startsWith("vmess://")) {
            outbound = buildVmess(raw);
        } else if (raw.startsWith("vless://")) {
            outbound = buildVless(raw);
        } else if (raw.startsWith("trojan://")) {
            outbound = buildTrojan(raw);
        } else if (raw.startsWith("ss://")) {
            outbound = buildShadowsocks(raw);
        } else {
            throw new IllegalArgumentException("unsupported config");
        }
        return baseConfig(outbound).toString();
    }

    private static JSONObject baseConfig(JSONObject outbound) throws Exception {
        JSONObject root = new JSONObject();
        root.put("log", new JSONObject().put("loglevel", "warning"));
        root.put("stats", new JSONObject());
        JSONObject policy = new JSONObject();
        policy.put("levels", new JSONObject().put("8", new JSONObject()
                .put("handshake", 4)
                .put("connIdle", 300)
                .put("uplinkOnly", 1)
                .put("downlinkOnly", 1)));
        policy.put("system", new JSONObject()
                .put("statsOutboundUplink", true)
                .put("statsOutboundDownlink", true));
        root.put("policy", policy);
        root.put("inbounds", new JSONArray().put(socksInbound()));
        root.put("outbounds", new JSONArray()
                .put(outbound)
                .put(new JSONObject().put("tag", "direct").put("protocol", "freedom").put("settings", new JSONObject().put("domainStrategy", "UseIP")))
                .put(new JSONObject().put("tag", "block").put("protocol", "blackhole")));
        root.put("routing", new JSONObject().put("domainStrategy", "AsIs").put("rules", new JSONArray()));
        root.put("dns", new JSONObject().put("hosts", new JSONObject()).put("servers", new JSONArray().put("1.1.1.1").put("8.8.8.8")));
        return root;
    }

    private static void ensureSocksInbound(JSONObject root) throws Exception {
        JSONArray inbounds = root.optJSONArray("inbounds");
        if (inbounds == null) {
            inbounds = new JSONArray();
            root.put("inbounds", inbounds);
        }
        for (int i = 0; i < inbounds.length(); i++) {
            JSONObject inbound = inbounds.optJSONObject(i);
            if (inbound != null && "socks".equals(inbound.optString("protocol"))) {
                inbound.put("listen", "127.0.0.1");
                inbound.put("port", SOCKS_PORT);
                return;
            }
        }
        inbounds.put(0, socksInbound());
    }

    private static JSONObject socksInbound() throws Exception {
        return new JSONObject()
                .put("tag", "socks-in")
                .put("listen", "127.0.0.1")
                .put("port", SOCKS_PORT)
                .put("protocol", "socks")
                .put("settings", new JSONObject()
                        .put("auth", "noauth")
                        .put("udp", true)
                        .put("userLevel", 8))
                .put("sniffing", new JSONObject()
                        .put("enabled", true)
                        .put("destOverride", new JSONArray().put("http").put("tls")));
    }

    private static JSONObject buildVmess(String raw) throws Exception {
        String payload = raw.substring("vmess://".length());
        JSONObject source = new JSONObject(decodeBase64(payload));
        JSONObject user = new JSONObject()
                .put("id", source.optString("id"))
                .put("alterId", parseInt(source.optString("aid"), 0))
                .put("security", emptyTo(source.optString("scy"), "auto"))
                .put("level", 8);
        JSONObject outbound = new JSONObject()
                .put("tag", "proxy")
                .put("protocol", "vmess")
                .put("settings", new JSONObject().put("vnext", new JSONArray().put(new JSONObject()
                        .put("address", source.optString("add"))
                        .put("port", parseInt(source.optString("port"), 443))
                        .put("users", new JSONArray().put(user)))))
                .put("streamSettings", streamSettings(source.optString("net"), source.optString("tls"), source.optString("host"), source.optString("path"), source.optString("sni"), source.optString("fp")))
                .put("mux", new JSONObject().put("enabled", false));
        return outbound;
    }

    private static JSONObject buildVless(String raw) throws Exception {
        Uri uri = Uri.parse(raw);
        JSONObject user = new JSONObject()
                .put("id", uri.getUserInfo())
                .put("encryption", emptyTo(uri.getQueryParameter("encryption"), "none"))
                .put("flow", emptyTo(uri.getQueryParameter("flow"), ""))
                .put("level", 8);
        return new JSONObject()
                .put("tag", "proxy")
                .put("protocol", "vless")
                .put("settings", new JSONObject().put("vnext", new JSONArray().put(new JSONObject()
                        .put("address", uri.getHost())
                        .put("port", uri.getPort() > 0 ? uri.getPort() : 443)
                        .put("users", new JSONArray().put(user)))))
                .put("streamSettings", streamSettings(uri.getQueryParameter("type"), uri.getQueryParameter("security"), uri.getQueryParameter("host"), uri.getQueryParameter("path"), uri.getQueryParameter("serviceName"), uri.getQueryParameter("sni"), uri.getQueryParameter("fp"), uri.getQueryParameter("pbk"), uri.getQueryParameter("sid"), uri.getQueryParameter("spx")))
                .put("mux", new JSONObject().put("enabled", false));
    }

    private static JSONObject buildTrojan(String raw) throws Exception {
        Uri uri = Uri.parse(raw);
        JSONObject server = new JSONObject()
                .put("address", uri.getHost())
                .put("port", uri.getPort() > 0 ? uri.getPort() : 443)
                .put("password", uri.getUserInfo())
                .put("level", 8);
        return new JSONObject()
                .put("tag", "proxy")
                .put("protocol", "trojan")
                .put("settings", new JSONObject().put("servers", new JSONArray().put(server)))
                .put("streamSettings", streamSettings(uri.getQueryParameter("type"), uri.getQueryParameter("security"), uri.getQueryParameter("host"), uri.getQueryParameter("path"), uri.getQueryParameter("serviceName"), uri.getQueryParameter("sni"), uri.getQueryParameter("fp"), uri.getQueryParameter("pbk"), uri.getQueryParameter("sid"), uri.getQueryParameter("spx")))
                .put("mux", new JSONObject().put("enabled", false));
    }

    private static JSONObject buildShadowsocks(String raw) throws Exception {
        String body = raw.substring("ss://".length());
        String name = "";
        int hash = body.indexOf('#');
        if (hash >= 0) {
            name = body.substring(hash + 1);
            body = body.substring(0, hash);
        }
        int at = body.lastIndexOf('@');
        String credentials;
        String hostPart;
        if (at >= 0) {
            credentials = body.substring(0, at);
            hostPart = body.substring(at + 1);
            String decoded = decodeBase64(credentials);
            if (!TextUtils.isEmpty(decoded) && decoded.contains(":")) {
                credentials = decoded;
            }
        } else {
            String decoded = decodeBase64(body);
            at = decoded.lastIndexOf('@');
            credentials = decoded.substring(0, at);
            hostPart = decoded.substring(at + 1);
        }
        int colon = credentials.indexOf(':');
        int portColon = hostPart.lastIndexOf(':');
        JSONObject server = new JSONObject()
                .put("address", hostPart.substring(0, portColon))
                .put("port", parseInt(hostPart.substring(portColon + 1), 8388))
                .put("method", credentials.substring(0, colon))
                .put("password", credentials.substring(colon + 1))
                .put("level", 8);
        return new JSONObject()
                .put("tag", "proxy")
                .put("protocol", "shadowsocks")
                .put("settings", new JSONObject().put("servers", new JSONArray().put(server)))
                .put("streamSettings", new JSONObject().put("network", "tcp"))
                .put("mux", new JSONObject().put("enabled", false));
    }

    private static JSONObject streamSettings(String network, String security, String host, String path, String sni, String fp) throws Exception {
        return streamSettings(network, security, host, path, null, sni, fp, null, null, null);
    }

    private static JSONObject streamSettings(String network, String security, String host, String path, String serviceName, String sni, String fp, String pbk, String sid, String spx) throws Exception {
        network = emptyTo(network, "tcp");
        JSONObject stream = new JSONObject().put("network", network);
        if (!TextUtils.isEmpty(security)) {
            stream.put("security", security);
        }
        if ("tls".equals(security) || "reality".equals(security)) {
            JSONObject tls = new JSONObject();
            if (!TextUtils.isEmpty(sni)) tls.put("serverName", sni);
            if (!TextUtils.isEmpty(fp)) tls.put("fingerprint", fp);
            if ("reality".equals(security)) {
                if (!TextUtils.isEmpty(pbk)) tls.put("publicKey", pbk);
                if (!TextUtils.isEmpty(sid)) tls.put("shortId", sid);
                if (!TextUtils.isEmpty(spx)) tls.put("spiderX", spx);
            }
            stream.put("reality".equals(security) ? "realitySettings" : "tlsSettings", tls);
        }
        if ("ws".equals(network)) {
            JSONObject ws = new JSONObject();
            if (!TextUtils.isEmpty(path)) ws.put("path", path);
            if (!TextUtils.isEmpty(host)) ws.put("headers", new JSONObject().put("Host", host));
            stream.put("wsSettings", ws);
        } else if ("grpc".equals(network)) {
            JSONObject grpc = new JSONObject();
            if (!TextUtils.isEmpty(serviceName)) grpc.put("serviceName", serviceName);
            else if (!TextUtils.isEmpty(path)) grpc.put("serviceName", path);
            stream.put("grpcSettings", grpc);
        } else if ("xhttp".equals(network) || "splithttp".equals(network)) {
            JSONObject xhttp = new JSONObject();
            if (!TextUtils.isEmpty(path)) xhttp.put("path", path);
            if (!TextUtils.isEmpty(host)) xhttp.put("host", host);
            stream.put("xhttpSettings", xhttp);
        }
        return stream;
    }

    private static String decodeBase64(String text) {
        String normalized = text.trim().replace('-', '+').replace('_', '/');
        int padding = normalized.length() % 4;
        if (padding > 0) normalized += "====".substring(padding);
        return new String(Base64.decode(normalized, Base64.DEFAULT), StandardCharsets.UTF_8);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String emptyTo(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }
}
