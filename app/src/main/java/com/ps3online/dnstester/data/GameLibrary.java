package com.ps3online.dnstester.data;

import android.content.Context;

import com.ps3online.dnstester.model.DnsCombo;
import com.ps3online.dnstester.model.GameProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GameLibrary {

    public final List<GameProfile> games = new ArrayList<>();
    public final List<DnsCombo> dnsCombos = new ArrayList<>();

    public static GameLibrary load(Context context) {
        GameLibrary library = new GameLibrary();

        try {
            String json = readAsset(context, "game_profiles.json");
            JSONObject root = new JSONObject(json);

            JSONArray gamesArray = root.optJSONArray("games");

            if (gamesArray != null) {
                for (int i = 0; i < gamesArray.length(); i++) {
                    JSONObject obj = gamesArray.optJSONObject(i);

                    if (obj == null) {
                        continue;
                    }

                    GameProfile game = parseGame(obj);
                    library.games.add(game);
                }
            }

            JSONArray dnsArray = root.optJSONArray("dns_combos");

            if (dnsArray != null) {
                for (int i = 0; i < dnsArray.length(); i++) {
                    JSONObject obj = dnsArray.optJSONObject(i);

                    if (obj == null) {
                        continue;
                    }

                    DnsCombo combo = parseDnsCombo(obj);
                    library.dnsCombos.add(combo);
                }
            }

            /*
             * Compatibilidad con el JSON anterior:
             * "profiles": [...]
             */
            if (gamesArray == null) {
                JSONArray oldProfiles = root.optJSONArray("profiles");

                if (oldProfiles != null) {
                    for (int i = 0; i < oldProfiles.length(); i++) {
                        JSONObject obj = oldProfiles.optJSONObject(i);

                        if (obj == null) {
                            continue;
                        }

                        String nombre = obj.optString("game", "");

                        if (nombre.length() == 0) {
                            continue;
                        }

                        GameProfile game = new GameProfile();

                        game.id = slug(nombre);
                        game.nombre = nombre;
                        game.psnActivo = false;
                        game.requiereVerificacion = false;

                        library.games.add(game);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return library;
    }

    private static GameProfile parseGame(JSONObject obj) {
        GameProfile game = new GameProfile();

        game.id = obj.optString("id", "");
        game.nombre = obj.optString("nombre", "");
        game.genero = nullable(
                obj.optString("genero", null)
        );

        game.psnActivo = obj.optBoolean(
                "psnActivo",
                false
        );

        game.requiereVerificacion = obj.optBoolean(
                "requiereVerificacion",
                false
        );

        game.comunidadId = nullable(
                obj.optString("comunidadId", null)
        );

        game.modPkgRequisito = nullable(
                obj.optString("modPkgRequisito", null)
        );

        game.versionRequisito = nullable(
                obj.optString("versionRequisito", null)
        );

        game.comandosConsola = nullable(
                obj.optString("comandosConsola", null)
        );

        game.servidor = nullable(
                obj.optString("servidor", null)
        );

        game.instrucciones = nullable(
                obj.optString("instrucciones", null)
        );

        game.notas = nullable(
                obj.optString("notas", null)
        );

        JSONArray aliases = obj.optJSONArray("alias");

        if (aliases != null) {
            for (int i = 0; i < aliases.length(); i++) {
                String alias = aliases.optString(i, "").trim();

                if (!alias.isEmpty()) {
                    game.alias.add(alias);
                }
            }
        }

        JSONArray combos = obj.optJSONArray("dnsComboIds");

        if (combos != null) {
            for (int i = 0; i < combos.length(); i++) {
                String comboId = combos.optString(i, "").trim();

                if (!comboId.isEmpty()) {
                    game.dnsComboIds.add(comboId);
                }
            }
        }

        return game;
    }

    private static DnsCombo parseDnsCombo(JSONObject obj) {
        DnsCombo combo = new DnsCombo();

        combo.id = obj.optString("id", "");

        combo.juegoId = nullable(
                obj.optString("juegoId", null)
        );

        combo.grupoId = nullable(
                obj.optString("grupoId", null)
        );

        combo.dnsPrimaria = nullable(
                obj.optString("dnsPrimaria", null)
        );

        combo.dnsSecundaria = nullable(
                obj.optString("dnsSecundaria", null)
        );

        combo.votosPositivos = obj.optInt(
                "votosPositivos",
                0
        );

        combo.votosNegativos = obj.optInt(
                "votosNegativos",
                0
        );

        combo.fuente = nullable(
                obj.optString("fuente", null)
        );

        combo.nota = nullable(
                obj.optString("nota", null)
        );

        combo.secundariaPublica = obj.optBoolean(
                "secundariaPublica",
                false
        );

        combo.fallback = obj.optBoolean(
                "fallback",
                false
        );

        String origen = obj.optString(
                "origen",
                "REGISTRADA"
        );

        try {
            combo.origen = DnsCombo.Origen.valueOf(origen);
        } catch (Exception e) {
            combo.origen = DnsCombo.Origen.REGISTRADA;
        }

        String estado = obj.optString(
                "estado",
                "AZUL_COMUNIDAD"
        );

        try {
            combo.estado = DnsCombo.Estado.valueOf(estado);
        } catch (Exception e) {
            combo.estado = DnsCombo.Estado.AZUL_COMUNIDAD;
        }

        JSONArray alternatives = obj.optJSONArray(
                "secundariasAlternativas"
        );

        if (alternatives != null) {
            for (int i = 0; i < alternatives.length(); i++) {
                String dns = alternatives.optString(
                        i,
                        ""
                ).trim();

                if (!dns.isEmpty()) {
                    combo.secundariasAlternativas.add(dns);
                }
            }
        }

        return combo;
    }

    private static String readAsset(
            Context context,
            String filename
    ) throws Exception {

        InputStream input = context.getAssets().open(filename);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        input,
                        StandardCharsets.UTF_8
                )
        );

        StringBuilder result = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            result.append(line).append('\n');
        }

        reader.close();

        return result.toString();
    }

    private static String nullable(String value) {
        if (value == null) {
            return null;
        }

        if (value.equals("null")) {
            return null;
        }

        if (value.trim().isEmpty()) {
            return null;
        }

        return value;
    }

    private static String slug(String value) {
        return value
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
    }
}
