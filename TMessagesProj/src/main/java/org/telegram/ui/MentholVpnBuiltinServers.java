package org.telegram.ui;

import java.util.ArrayList;

public class MentholVpnBuiltinServers {
    public static final String VLESS_RAW = "vless://df3899dc-f5e0-4d4e-ad74-0dbe9c53173f@109.71.240.191:445?type=grpc&encryption=none&security=reality&pbk=mr6pvCeUkQCaRcpbWvZ1ixoKlzZBEFTRlsN0uFcqbic&fp=chrome&sni=max.ru&sid=c5204446&serviceName=grpc&flow=#%D0%9D%D0%B8%D0%B4%D0%B5%D1%80%D0%BB%D0%B0%D0%BD%D0%B4%D1%8B%201%20%F0%9F%87%B3%F0%9F%87%B1";

    public static ArrayList<MentholVpnConfigParser.ParsedConfig> getConfigs() {
        ArrayList<MentholVpnConfigParser.ParsedConfig> configs = new ArrayList<>();
        add(configs, hysteria("178.18.147.66", 29900, "Server 1"));
        add(configs, VLESS_RAW, "Server 2");
        add(configs, hysteria("178.18.147.66", 29900, "Server 3"));
        add(configs, hysteria("83.243.86.103", 38563, "Server 4"));
        add(configs, hysteria("83.243.86.103", 12855, "Server 5"));
        return configs;
    }

    private static void add(ArrayList<MentholVpnConfigParser.ParsedConfig> configs, String raw) {
        add(configs, raw, null);
    }

    private static void add(ArrayList<MentholVpnConfigParser.ParsedConfig> configs, String raw, String name) {
        MentholVpnConfigParser.ParsedConfig config = MentholVpnConfigParser.parseConfig(raw);
        if (config != null) {
            if (name != null) {
                config.name = name;
            }
            configs.add(config);
        }
    }

    private static String hysteria(String address, int port, String remarks) {
        return "{"
                + "\"remarks\":\"" + remarks + "\"," 
                + "\"log\":{\"loglevel\":\"warning\"},"
                + "\"stats\":{},"
                + "\"inbounds\":[{\"listen\":\"127.0.0.1\",\"port\":" + MentholVpnConfigBuilder.SOCKS_PORT + ",\"protocol\":\"socks\",\"settings\":{\"auth\":\"noauth\",\"udp\":true,\"userLevel\":8},\"sniffing\":{\"enabled\":true,\"destOverride\":[\"http\",\"tls\",\"quic\"]},\"tag\":\"socks\"}],"
                + "\"outbounds\":[{\"tag\":\"proxy\",\"protocol\":\"hysteria\",\"settings\":{\"address\":\"" + address + "\",\"port\":" + port + ",\"version\":2},\"streamSettings\":{\"network\":\"hysteria\",\"security\":\"tls\",\"hysteriaSettings\":{\"auth\":\"github.com/Alvin9999-newpac/fanqiang\",\"version\":2},\"tlsSettings\":{\"allowInsecure\":true,\"alpn\":[\"h3\"],\"serverName\":\"www.microsoft.com\",\"show\":false}},\"mux\":{\"enabled\":false,\"concurrency\":-1,\"xudpConcurrency\":8,\"xudpProxyUDP443\":\"\"}},{\"tag\":\"direct\",\"protocol\":\"freedom\",\"settings\":{\"domainStrategy\":\"UseIP\"}},{\"tag\":\"block\",\"protocol\":\"blackhole\"}],"
                + "\"routing\":{\"domainStrategy\":\"IPIfNonMatch\",\"rules\":[]},"
                + "\"dns\":{\"queryStrategy\":\"UseIPv4\",\"servers\":[\"1.1.1.1\",\"8.8.8.8\"]}"
                + "}";
    }
}
