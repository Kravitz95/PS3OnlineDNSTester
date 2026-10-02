package com.ps3online.dnstester.data;

import com.ps3online.dnstester.model.DnsCombo;
import com.ps3online.dnstester.model.GameProfile;

import java.util.Locale;

public final class CommunityRegistry {

    private CommunityRegistry() {
    }

    public static final class CommunityInfo {
        public final String id;
        public final String name;
        public final String url;

        public CommunityInfo(String id, String name, String url) {
            this.id = id;
            this.name = name;
            this.url = url;
        }
    }

    private static final CommunityInfo COUNTER_STRIKE = new CommunityInfo(
            "counter_strike_go",
            "Counter-Strike: GO",
            "https://discord.gg/VGxRxgfzzF"
    );

    private static final CommunityInfo GRAN_TURISMO = new CommunityInfo(
            "gran_turismo_online",
            "Gran Turismo Online",
            "https://discord.gg/gt-online-community-839257361486708816"
    );

    private static final CommunityInfo BATTLEFIELD = new CommunityInfo(
            "battlefield",
            "Battlefield",
            "https://discord.gg/tydUZNsMtq"
    );

    private static final CommunityInfo MORTAL_KOMBAT = new CommunityInfo(
            "mortal_kombat",
            "Mortal Kombat",
            "https://discord.gg/dQK3AYGKeQ"
    );

    private static final CommunityInfo GTA_V = new CommunityInfo(
            "re_v",
            "RE-V · GTA V",
            "https://discord.gg/re-v-1236628256011063332"
    );

    private static final CommunityInfo UNCHARTED_RELOADED = new CommunityInfo(
            "uncharted_reloaded",
            "Uncharted Reloaded",
            "https://discord.gg/87EyTdE5tS"
    );

    private static final CommunityInfo ROCKET_SARPBC = new CommunityInfo(
            "rocket_league_sarpbc",
            "Rocket League (SARPBC+)",
            "https://discord.com/channels/735213000239349863/1407835364726538341"
    );

    private static final CommunityInfo PS3_REBORN = new CommunityInfo(
            "ps3_reborn",
            "PS3 Reborn",
            "https://discord.gg/YpxBTdCbyJ"
    );

    private static final CommunityInfo PS3_ONLINE_LAN = new CommunityInfo(
            "ps3_online_lan",
            "PS3 Online y LAN",
            "https://discord.gg/UFEanvEkRS"
    );

    public static CommunityInfo getForGame(GameProfile game) {
        if (game == null) {
            return null;
        }

        String id = normalize(game.id);
        String name = normalize(game.nombre);

        if ("uncharted_reloaded".equals(normalize(game.comunidadId))) {
            return UNCHARTED_RELOADED;
        }

        if ("tlou".equals(id)
                || "uncharted2".equals(id)
                || "uncharted3".equals(id)) {
            return UNCHARTED_RELOADED;
        }

        if (containsAny(name, "counter strike", "counter-strike", "counter strike: go", "cs:go")) {
            return COUNTER_STRIKE;
        }

        if (containsAny(name, "gran turismo 5", "gran turismo 6")) {
            return GRAN_TURISMO;
        }

        if (name.startsWith("battlefield")) {
            return BATTLEFIELD;
        }

        if (containsAny(name, "mortal kombat 9", "mortal kombat")) {
            return MORTAL_KOMBAT;
        }

        if (containsAny(name, "grand theft auto v", "gta v", "grand theft auto 5")) {
            return GTA_V;
        }

        if (containsAny(name, "rocket league", "sarpbc")) {
            return ROCKET_SARPBC;
        }

        return null;
    }

    public static CommunityInfo getForDns(DnsCombo combo) {
        if (combo == null) {
            return null;
        }

        String groupId = normalize(combo.grupoId);

        if ("ps3_reborn".equals(groupId)) {
            return PS3_REBORN;
        }

        return null;
    }

    public static CommunityInfo getPs3OnlineLan() {
        return PS3_ONLINE_LAN;
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(normalize(term))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.US);
    }
}
