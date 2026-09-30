package com.ps3online.dnstester.data;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GameSearchService {

    public static class SearchResult {
        public String name;
        public String platform;
        public String source;

        public SearchResult(
                String name,
                String platform,
                String source
        ) {
            this.name = name;
            this.platform = platform;
            this.source = source;
        }
    }

    public interface CallbackResult {
        void onSuccess(List<SearchResult> results);
        void onError(String message);
    }

    private static final String WIKIDATA_API =
            "https://www.wikidata.org/w/api.php";

    private static final String PS3_ENTITY_ID =
            "Q10683";

    private final OkHttpClient client =
            new OkHttpClient();

    public void search(
            String query,
            CallbackResult callback
    ) {

        if (query == null ||
                query.trim().isEmpty()) {

            callback.onSuccess(
                    new ArrayList<>()
            );
            return;
        }

        try {

            String encoded =
                    URLEncoder.encode(
                            query.trim(),
                            StandardCharsets.UTF_8.toString()
                    );

            String url =
                    WIKIDATA_API +
                    "?action=wbsearchentities" +
                    "&search=" + encoded +
                    "&language=en" +
                    "&uselang=en" +
                    "&format=json" +
                    "&limit=10";

            Request request =
                    new Request.Builder()
                            .url(url)
                            .header(
                                    "User-Agent",
                                    "PS3OnlineConnect/4.0"
                            )
                            .get()
                            .build();

            client.newCall(request).enqueue(
                    new Callback() {

                        @Override
                        public void onFailure(
                                Call call,
                                IOException e
                        ) {

                            callback.onError(
                                    "No se pudo realizar " +
                                    "la búsqueda externa."
                            );
                        }

                        @Override
                        public void onResponse(
                                Call call,
                                Response response
                        ) {

                            try {

                                if (!response.isSuccessful()) {

                                    callback.onError(
                                            "Búsqueda externa: HTTP " +
                                            response.code()
                                    );
                                    return;
                                }

                                String body =
                                        response.body() == null
                                                ? ""
                                                : response.body()
                                                        .string();

                                JSONObject root =
                                        new JSONObject(body);

                                JSONArray search =
                                        root.optJSONArray(
                                                "search"
                                        );

                                List<SearchResult> results =
                                        new ArrayList<>();

                                if (search == null) {

                                    callback.onSuccess(
                                            results
                                    );
                                    return;
                                }

                                /*
                                 * Primero obtenemos los candidatos
                                 * encontrados por nombre.
                                 */
                                List<String> entityIds =
                                        new ArrayList<>();

                                List<String> labels =
                                        new ArrayList<>();

                                for (int i = 0;
                                     i < search.length();
                                     i++) {

                                    JSONObject item =
                                            search.optJSONObject(i);

                                    if (item == null) {
                                        continue;
                                    }

                                    String id =
                                            item.optString(
                                                    "id",
                                                    ""
                                            ).trim();

                                    String label =
                                            item.optString(
                                                    "label",
                                                    ""
                                            ).trim();

                                    if (id.isEmpty() ||
                                            label.isEmpty()) {
                                        continue;
                                    }

                                    entityIds.add(id);
                                    labels.add(label);
                                }

                                /*
                                 * Wikidata permite solicitar varias
                                 * entidades en una sola petición.
                                 */
                                if (entityIds.isEmpty()) {

                                    callback.onSuccess(
                                            results
                                    );
                                    return;
                                }

                                StringBuilder ids =
                                        new StringBuilder();

                                for (String id : entityIds) {

                                    if (ids.length() > 0) {
                                        ids.append("|");
                                    }

                                    ids.append(id);
                                }

                                String entityUrl =
                                        "https://www.wikidata.org/wiki/Special:EntityData/"
                                        + ids
                                        + ".json";

                                Request entityRequest =
                                        new Request.Builder()
                                                .url(entityUrl)
                                                .header(
                                                        "User-Agent",
                                                        "PS3OnlineConnect/4.0"
                                                )
                                                .get()
                                                .build();

                                client.newCall(
                                        entityRequest
                                ).enqueue(
                                        new Callback() {

                                            @Override
                                            public void onFailure(
                                                    Call call,
                                                    IOException e
                                            ) {

                                                callback.onError(
                                                        "No se pudieron verificar "
                                                        + "las plataformas PS3."
                                                );
                                            }

                                            @Override
                                            public void onResponse(
                                                    Call call,
                                                    Response response
                                            ) {

                                                try {

                                                    if (!response
                                                            .isSuccessful()) {

                                                        callback.onError(
                                                                "Verificación "
                                                                + "de plataforma: HTTP "
                                                                + response.code()
                                                        );
                                                        return;
                                                    }

                                                    String entityBody =
                                                            response.body()
                                                                    == null
                                                                    ? ""
                                                                    : response.body()
                                                                            .string();

                                                    JSONObject entityRoot =
                                                            new JSONObject(
                                                                    entityBody
                                                            );

                                                    JSONObject entities =
                                                            entityRoot
                                                                    .optJSONObject(
                                                                            "entities"
                                                                    );

                                                    if (entities == null) {

                                                        callback.onSuccess(
                                                                results
                                                        );
                                                        return;
                                                    }

                                                    /*
                                                     * Revisamos P400 =
                                                     * plataforma.
                                                     */
                                                    for (int i = 0;
                                                         i < entityIds.size();
                                                         i++) {

                                                        String entityId =
                                                                entityIds.get(i);

                                                        JSONObject entity =
                                                                entities
                                                                        .optJSONObject(
                                                                                entityId
                                                                        );

                                                        if (entity == null) {
                                                            continue;
                                                        }

                                                        JSONObject claims =
                                                                entity.optJSONObject(
                                                                        "claims"
                                                                );

                                                        if (claims == null) {
                                                            continue;
                                                        }

                                                        JSONArray platforms =
                                                                claims.optJSONArray(
                                                                        "P400"
                                                                );

                                                        if (platforms == null) {
                                                            continue;
                                                        }

                                                        boolean isPs3 =
                                                                false;

                                                        for (int p = 0;
                                                             p < platforms.length();
                                                             p++) {

                                                            JSONObject statement =
                                                                    platforms
                                                                            .optJSONObject(
                                                                                    p
                                                                            );

                                                            if (statement == null) {
                                                                continue;
                                                            }

                                                            JSONObject mainsnak =
                                                                    statement
                                                                            .optJSONObject(
                                                                                    "mainsnak"
                                                                            );

                                                            if (mainsnak == null) {
                                                                continue;
                                                            }

                                                            JSONObject dataValue =
                                                                    mainsnak
                                                                            .optJSONObject(
                                                                                    "datavalue"
                                                                            );

                                                            if (dataValue == null) {
                                                                continue;
                                                            }

                                                            JSONObject value =
                                                                    dataValue
                                                                            .optJSONObject(
                                                                                    "value"
                                                                            );

                                                            if (value == null) {
                                                                continue;
                                                            }

                                                            String platformId =
                                                                    value.optString(
                                                                            "id",
                                                                            ""
                                                                    );

                                                            if (PS3_ENTITY_ID
                                                                    .equals(
                                                                            platformId
                                                                    )) {

                                                                isPs3 = true;
                                                                break;
                                                            }
                                                        }

                                                        if (!isPs3) {
                                                            continue;
                                                        }

                                                        String name =
                                                                labels.get(i);

                                                        results.add(
                                                                new SearchResult(
                                                                        name,
                                                                        "PlayStation 3",
                                                                        "Wikidata"
                                                                )
                                                        );
                                                    }

                                                    callback.onSuccess(
                                                            results
                                                    );

                                                } catch (Exception e) {

                                                    callback.onError(
                                                            "Respuesta de "
                                                            + "plataforma inválida."
                                                    );
                                                }
                                            }
                                        }
                                );

                            } catch (Exception e) {

                                callback.onError(
                                        "Respuesta de búsqueda inválida."
                                );
                            }
                        }
                    }
            );

        } catch (Exception e) {

            callback.onError(
                    "No se pudo preparar la búsqueda."
            );
        }
    }
}
