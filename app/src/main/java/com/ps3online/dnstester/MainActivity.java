package com.ps3online.dnstester;
import android.content.Intent;
import android.net.Uri;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import android.widget.AutoCompleteTextView;

import com.ps3online.dnstester.model.GameProfile;
import com.ps3online.dnstester.data.GameLibrary;
import com.ps3online.dnstester.data.CommunityRegistry;
import com.ps3online.dnstester.model.DnsCombo;
import com.ps3online.dnstester.dns.DnsTester;
import com.ps3online.dnstester.p2p.P2PSession;
import com.ps3online.dnstester.stun.StunP2P;
import com.ps3online.dnstester.data.GameSearchService;
import com.ps3online.dnstester.system.SystemStatusService;
import com.ps3online.dnstester.system.OnlinePresenceService;
import com.ps3online.dnstester.interpretation.ResultInterpreter;
import com.ps3online.dnstester.interpretation.TestResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private ScrollView mainScroll;
    private LinearLayout resultPanel;
    private TextView resultHeader;
    private TextView resultStatus;
    private Button resultCopyButton;
    private Button resultRepeatButton;
    private String lastResultText = "";

    private AutoCompleteTextView gameSelector;
    private AutoCompleteTextView dnsSelector;
    private TextView gameInfo;
    private TextView verificationCard;
    private TextView requirementCard;
    private TextView status;

    // Comunidad dinámica asociada al juego o al DNS seleccionado.
    private TextView communityInfo;
    private Button communityButton;
    private LinearLayout communityListContainer;
    private final List<CommunityRegistry.CommunityInfo> selectedCommunities =
            new ArrayList<>();

    private EditText customDns;
    private Button testButton;
    private Button copyButton;

    // P2P / conectividad
    private P2PSession p2pSession;
    private String p2pRoomCode = "";
    private boolean p2pRunning = false;

    private final List<GameProfile> games = new ArrayList<>();

    // Perfil local del juego actualmente seleccionado.
    // Permite que el diagnóstico use los metadatos reales de la biblioteca.
    private GameProfile selectedGameProfile;
    private final List<DnsCombo> dnsCombos = new ArrayList<>();
    private GameLibrary gameLibrary;
private GameSearchService gameSearchService;
private ArrayAdapter<String> gameSearchAdapter;
private final List<String> gameSearchResults = new ArrayList<>();
private final ExecutorService dnsExecutor = Executors.newFixedThreadPool(2);
    private LinearLayout systemStatusContainer;
    private TextView systemSummary;
    private boolean systemStatusRunning = false;

    private TextView onlineUsers;

    private OnlinePresenceService onlinePresenceService;


    /**
     * Actualiza la tarjeta de comunidades relacionadas.
     *
     * Cada nombre funciona como enlace táctil independiente.
     */
    /**
     * Actualiza la tarjeta de comunidades relacionadas.
     * Cada comunidad se muestra como una fila tactil independiente.
     */
    private void updateCommunityCard() {
        if (communityInfo == null || communityListContainer == null) {
            return;
        }

        communityListContainer.removeAllViews();

        if (selectedCommunities.isEmpty()) {
            communityInfo.setText("👥 Selecciona un juego o una DNS para mostrar sus comunidades relacionadas.");
            communityInfo.setOnClickListener(null);

            if (communityButton != null) {
                communityButton.setVisibility(View.GONE);
            }
            return;
        }

        communityInfo.setText("👥 COMUNIDADES RELACIONADAS");
        communityInfo.setTextColor(android.graphics.Color.CYAN);
        communityInfo.setOnClickListener(null);

        if (communityButton != null) {
            communityButton.setVisibility(View.GONE);
        }

        for (CommunityRegistry.CommunityInfo community : selectedCommunities) {
            TextView communityRow = new TextView(this);

            communityRow.setText("👥  " + community.name);
            communityRow.setTextSize(16);
            communityRow.setGravity(Gravity.CENTER_VERTICAL);
            communityRow.setPadding(20, 18, 20, 18);
            communityRow.setClickable(true);
            communityRow.setFocusable(true);

            communityRow.setOnClickListener(v -> openCommunity(community));

            communityListContainer.addView(communityRow);
        }
    }


    /**
     * Abre la comunidad seleccionada usando la URL registrada.
     */
    private void openCommunity(
            CommunityRegistry.CommunityInfo community
    ) {
        if (community == null
                || community.url == null
                || community.url.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "⚠️ Esta comunidad no tiene un enlace configurado.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(community.url)
            );

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "⚠️ No se pudo abrir la comunidad.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    /**
     * Determina todas las comunidades aplicables al juego y DNS actual.
     * Se eliminan duplicados por ID.
     */
    private List<CommunityRegistry.CommunityInfo> resolveCommunities(
            GameProfile game,
            DnsCombo combo
    ) {
        List<CommunityRegistry.CommunityInfo> result =
                new ArrayList<>();

        CommunityRegistry.CommunityInfo gameCommunity =
                CommunityRegistry.getForGame(game);

        if (gameCommunity != null) {
            addCommunityIfMissing(result, gameCommunity);
        }

        CommunityRegistry.CommunityInfo dnsCommunity =
                CommunityRegistry.getForDns(combo);

        if (dnsCommunity != null) {
            addCommunityIfMissing(result, dnsCommunity);
        }

        if (result.isEmpty()) {
            result.add(CommunityRegistry.getPs3OnlineLan());
        }

        return result;
    }

    /**
     * Agrega una comunidad evitando duplicados.
     */
    private void addCommunityIfMissing(
            List<CommunityRegistry.CommunityInfo> list,
            CommunityRegistry.CommunityInfo community
    ) {
        if (community == null) {
            return;
        }

        for (CommunityRegistry.CommunityInfo existing : list) {
            if (existing.id != null &&
                    existing.id.equalsIgnoreCase(community.id)) {
                return;
            }
        }

        list.add(community);
    }

    /**
     * Mantiene compatibilidad con la verificación individual.
     */
    private CommunityRegistry.CommunityInfo resolveCommunity(
            GameProfile game,
            DnsCombo combo
    ) {
        List<CommunityRegistry.CommunityInfo> communities =
                resolveCommunities(game, combo);

        return communities.isEmpty()
                ? CommunityRegistry.getPs3OnlineLan()
                : communities.get(0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

loadGameLibrary();
gameSearchService = new GameSearchService();
buildInterface();
    }

    private void loadGameLibrary() {

        gameLibrary = GameLibrary.load(this);

        games.clear();
        dnsCombos.clear();

        if (gameLibrary != null) {
            games.addAll(gameLibrary.games);
            dnsCombos.addAll(gameLibrary.dnsCombos);
        }

        /*
         * La biblioteca es la fuente de datos.
         * MainActivity ya no crea juegos manualmente.
         */
        if (games.isEmpty()) {
            GameProfile fallback = new GameProfile();
            fallback.id = "ps3_psn_general";
            fallback.nombre = "PS3/PSN general";
            fallback.psnActivo = false;
            fallback.requiereVerificacion = false;

            games.add(fallback);
        }
    }

    private void buildInterface() {

        mainScroll = new ScrollView(this);
        mainScroll.setFillViewport(true);
        mainScroll.setClipToPadding(false);
        mainScroll.setPadding(0, 0, 0, 80);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(32, 28, 32, 100);

        mainScroll.addView(root);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 64, 0, 28);

        onlineUsers = new TextView(this);
        onlineUsers.setText("👥 0 online");
        onlineUsers.setTextSize(12);
        onlineUsers.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams sideParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        headerRow.addView(onlineUsers, sideParams);

        TextView headerTitle = new TextView(this);
        headerTitle.setText("🎮 PS3 ONLINE DNS TEST");
        headerTitle.setTextSize(20);
        headerTitle.setGravity(Gravity.CENTER);

        headerRow.addView(headerTitle,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        2f
                ));

TextView discordHeader = new TextView(this);
discordHeader.setText("💬 Discord");
discordHeader.setTextSize(12);
discordHeader.setGravity(Gravity.CENTER);
discordHeader.setPadding(8, 12, 8, 12);
discordHeader.setClickable(true);
discordHeader.setFocusable(true);

discordHeader.setOnClickListener(view -> {
    Intent discordIntent = new Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://discord.gg/q88jScYgnS")
    );
    startActivity(discordIntent);
});

headerRow.addView(discordHeader, sideParams);
        root.addView(headerRow,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));


        gameSelector = new AutoCompleteTextView(this);

        gameSelector.setHint("1. 🔍 Buscar juego...");
        gameSelector.setSingleLine(true);
        gameSelector.setTextSize(16);
        gameSelector.setThreshold(0);
        gameSelector.setPadding(24, 16, 24, 16);

       List<String> initialGameNames =
        new ArrayList<>();

for (GameProfile game : games) {
    if (game.nombre != null &&
            !game.nombre.trim().isEmpty()) {
        initialGameNames.add(game.nombre);
    }
}

gameSearchResults.clear();
gameSearchResults.addAll(initialGameNames);

gameSearchAdapter =
        new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                gameSearchResults
        );

gameSelector.setAdapter(gameSearchAdapter);

gameSelector.addTextChangedListener(
        new android.text.TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {

                String query =
                        s == null
                                ? ""
                                : s.toString().trim();

                if (query.isEmpty()) {

                    gameSearchResults.clear();

                    for (GameProfile game : games) {
                        if (game.nombre != null &&
                                !game.nombre.trim().isEmpty()) {
                            gameSearchResults.add(
                                    game.nombre
                            );
                        }
                    }

                    gameSearchAdapter.notifyDataSetChanged();
                    return;
                }

                /*
                 * Primero mostramos los juegos registrados
                 * que coincidan con lo escrito.
                 */
                gameSearchResults.clear();

                String queryLower =
                        query.toLowerCase();

                for (GameProfile game : games) {

                    if (game.nombre == null) {
                        continue;
                    }

                    if (game.nombre
                            .toLowerCase()
                            .contains(queryLower)) {

                        if (!gameSearchResults
                                .contains(game.nombre)) {

                            gameSearchResults.add(
                                    game.nombre
                            );
                        }
                    }
                }

                gameSearchAdapter.notifyDataSetChanged();

                /*
                 * Indicador de búsqueda externa.
                 */
                gameInfo.setText(
                        "🔎 BUSCANDO EN INTERNET...\n" +
                        "Comprobando juegos PS3 disponibles."
                );

                gameSearchService.search(
                        query,
                        new GameSearchService.CallbackResult() {

                            @Override
                            public void onSuccess(
                                    List<GameSearchService.SearchResult>
                                            results
                            ) {

                                runOnUiThread(() -> {

                                    /*
                                     * Evitamos que una respuesta
                                     * antigua reemplace una búsqueda
                                     * más reciente.
                                     */
                                    String current =
                                            gameSelector
                                                    .getText()
                                                    .toString()
                                                    .trim();

                                    if (!current.equals(query)) {
                                        return;
                                    }

                                    for (
                                            GameSearchService.SearchResult
                                                    result : results
                                    ) {

                                        if (result == null ||
                                                result.name == null ||
                                                result.name
                                                        .trim()
                                                        .isEmpty()) {
                                            continue;
                                        }

                                        if (!gameSearchResults
                                                .contains(
                                                        result.name
                                                )) {

                                            gameSearchResults.add(
                                                    result.name
                                            );
                                        }
                                    }

                                    gameSearchAdapter
                                            .notifyDataSetChanged();

                                    if (results.isEmpty()) {

                                        gameInfo.setText(
                                                "🔎 " + query +
                                                "\n" +
                                                "No se encontraron " +
                                                "resultados PS3 externos."
                                        );

                                    } else {

                                        gameInfo.setText(
                                                "🌐 RESULTADOS EXTERNOS\n" +
                                                results.size() +
                                                " juego(s) PS3 encontrado(s) " +
                                                "en Wikidata."
                                        );
                                    }

                                    gameSelector.showDropDown();
                                });
                            }

                            @Override
                            public void onError(
                                    String message
                            ) {

                                runOnUiThread(() -> {

                                    String current =
                                            gameSelector
                                                    .getText()
                                                    .toString()
                                                    .trim();

                                    if (!current.equals(query)) {
                                        return;
                                    }

                                    gameInfo.setText(
                                            "⚠️ Búsqueda externa no disponible.\n" +
                                            "Los juegos registrados siguen disponibles."
                                    );
                                });
                            }
                        }
                );
            }

            @Override
            public void afterTextChanged(
                    android.text.Editable s
            ) {
            }
        }
);

gameSearchAdapter =
        new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                gameSearchResults
        );
        LinearLayout.LayoutParams spinnerParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        spinnerParams.setMargins(0, 0, 0, 8);
        root.addView(gameSelector, spinnerParams);

        gameInfo = new TextView(this);
        gameInfo.setTextSize(16);
        gameInfo.setGravity(Gravity.CENTER);
        gameInfo.setPadding(16, 16, 16, 16);
        root.addView(gameInfo);

        verificationCard = new TextView(this);
        verificationCard.setTextSize(16);
        verificationCard.setGravity(Gravity.CENTER);
        verificationCard.setPadding(20, 18, 20, 18);
        verificationCard.setVisibility(View.GONE);
        root.addView(verificationCard);

        requirementCard = new TextView(this);
        requirementCard.setTextSize(16);
        requirementCard.setGravity(Gravity.CENTER);
        requirementCard.setPadding(20, 18, 20, 18);
        requirementCard.setVisibility(View.GONE);
        root.addView(requirementCard);

TextView dnsTitle = new TextView(this);
dnsTitle.setText("2. 🌐 DNS");
dnsTitle.setTextSize(18);
dnsTitle.setGravity(Gravity.START);
dnsTitle.setPadding(0, 24, 0, 2);
root.addView(dnsTitle);

TextView dnsInstruction = new TextView(this);
dnsInstruction.setText(
        "Selecciona una DNS o introduce una personalizada"
);
dnsInstruction.setTextSize(13);
dnsInstruction.setGravity(Gravity.START);
dnsInstruction.setPadding(0, 0, 0, 10);
root.addView(dnsInstruction);
        TextView dnsPrimaryLabel = new TextView(this);
        dnsPrimaryLabel.setText("🔵 DNS primaria");
        dnsPrimaryLabel.setTextSize(15);
        dnsPrimaryLabel.setPadding(0, 8, 0, 4);
        root.addView(dnsPrimaryLabel);

        dnsSelector = new AutoCompleteTextView(this);
        dnsSelector.setHint("Buscar DNS primaria...");
        dnsSelector.setTextSize(16);
        dnsSelector.setSingleLine(true);
        dnsSelector.setThreshold(0);

        ArrayAdapter<String> dnsAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_dropdown_item_1line,
                        new ArrayList<>()
                );

        dnsSelector.setAdapter(dnsAdapter);

        root.addView(
                dnsSelector,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView secondaryLabel = new TextView(this);
        secondaryLabel.setText("🌐 DNS secundaria");
        secondaryLabel.setTextSize(15);
        secondaryLabel.setPadding(0, 12, 0, 4);
        root.addView(secondaryLabel);

        customDns = new EditText(this);
        customDns.setHint("DNS secundaria...");
        customDns.setInputType(InputType.TYPE_CLASS_TEXT);
        customDns.setSingleLine(true);
        customDns.setTextSize(16);

        LinearLayout.LayoutParams secondaryParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        root.addView(customDns, secondaryParams);

        TextView dnsStatus = new TextView(this);
        dnsStatus.setText(
                "Selecciona primero un juego para cargar sus DNS registradas."
        );
        dnsStatus.setTextSize(13);
        dnsStatus.setPadding(0, 8, 0, 4);
        root.addView(dnsStatus);

        // Combos actualmente disponibles para el juego seleccionado.
        final List<DnsCombo> selectedDnsCombos = new ArrayList<>();

        // Al tocar/escribir la primaria se muestran solamente los combos
        // correspondientes al juego actualmente seleccionado.
        dnsSelector.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                dnsAdapter.clear();

                String selectedName =
                        gameSelector.getText().toString().trim();

                GameProfile selectedGame = null;

                for (GameProfile candidate : games) {
                    if (candidate.nombre.equalsIgnoreCase(selectedName)) {
                        selectedGame = candidate;
                        break;
                    }

                    for (String alias : candidate.alias) {
                        if (alias.equalsIgnoreCase(selectedName)) {
                            selectedGame = candidate;
                            break;
                        }
                    }

                    if (selectedGame != null) {
                        break;
                    }
                }

                selectedDnsCombos.clear();

                if (selectedGame != null) {

                    for (String comboId : selectedGame.dnsComboIds) {

                        for (DnsCombo combo : dnsCombos) {

                            if (combo.id.equals(comboId)) {
                                selectedDnsCombos.add(combo);

                                String icon = "🔵";

                                if (combo.origen ==
                                        DnsCombo.Origen.SUGERIDA_API) {
                                    icon = "🟡";
                                } else if (combo.origen ==
                                        DnsCombo.Origen.PERSONALIZADA) {
                                    icon = "⚪";
                                }

                                String secondary =
                                        combo.dnsSecundaria == null
                                                ? ""
                                                : combo.dnsSecundaria;

                                String display =
                                        icon + " " +
                                        combo.dnsPrimaria +
                                        (secondary.isEmpty()
                                                ? ""
                                                : " / " + secondary);

                                dnsAdapter.add(display);
                            }
                        }
                    }

                    if (selectedDnsCombos.isEmpty()) {
                        dnsStatus.setText(
                                "⚪ Este juego no tiene todavía una DNS registrada."
                        );
                    } else {
                        dnsStatus.setText(
                                "🔵 DNS registradas para " +
                                selectedGame.nombre
                        );
                    }

                } else {
                    dnsStatus.setText(
                            "⚠️ Selecciona un juego registrado primero."
                    );
                }

                dnsAdapter.notifyDataSetChanged();
                dnsSelector.showDropDown();
            }
        });

        // Selección de un combo: primaria + secundaria se rellenan juntas.
        dnsSelector.setOnItemClickListener(
                (parent, view, position, id) -> {

                    if (position < 0 ||
                            position >= selectedDnsCombos.size()) {
                        return;
                    }

                    DnsCombo combo =
                            selectedDnsCombos.get(position);

                    // Reúne todas las comunidades del juego y del DNS.
                    selectedCommunities.clear();
                    selectedCommunities.addAll(
                            resolveCommunities(
                                    selectedGameProfile,
                                    combo
                            )
                    );

                    updateCommunityCard();

                    if (combo.dnsPrimaria != null) {
                        dnsSelector.setText(
                                combo.dnsPrimaria,
                                false
                        );
                    }

                    if (combo.dnsSecundaria != null) {
                        customDns.setText(
                                combo.dnsSecundaria
                        );
                    }

                    String secondaryInfo =
                            combo.secundariaPublica
                                    ? " · secundaria pública editable"
                                    : "";

                    dnsStatus.setText(
                            "🔵 COMBO REGISTRADO" +
                            secondaryInfo +
                            "\n" +
                            combo.dnsPrimaria +
                            " / " +
                            (combo.dnsSecundaria == null
                                    ? ""
                                    : combo.dnsSecundaria)
                    );
                }
        );

        // Si el usuario escribe una primaria manualmente, no inventamos
        // una secundaria. El usuario conserva control sobre ambos campos.
        dnsSelector.addTextChangedListener(
                new android.text.TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count) {

                        if (s == null) {
                            return;
                        }

                        String typed =
                                s.toString().trim();

                        if (typed.isEmpty()) {
                            return;
                        }

                        boolean registered = false;

                        for (DnsCombo combo : selectedDnsCombos) {
                            if (typed.equals(
                                    combo.dnsPrimaria)) {
                                registered = true;
                                break;
                            }
                        }

                        if (!registered) {
                            dnsStatus.setText(
                                    "⚪ DNS primaria personalizada. " +
                                    "Introduce o conserva la secundaria que quieras utilizar."
                            );
                        }
                    }

                    @Override
                    public void afterTextChanged(
                            android.text.Editable s) {
                    }
                }
        );

        testButton = new Button(this);
        testButton.setText(
                "🔎 Probar DNS"
        );
        testButton.setAllCaps(false);

        LinearLayout.LayoutParams testParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        testParams.setMargins(0, 16, 0, 0);
        root.addView(testButton, testParams);

        testButton.setOnClickListener(v -> testDnsSimultaneously());

        copyButton = new Button(this);
        copyButton.setText(
                "📋 COPIAR CONFIGURACIÓN"
        );
        copyButton.setAllCaps(false);
        root.addView(
                copyButton,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView connectivityTitle = new TextView(this);
        connectivityTitle.setText("3. 🧪 PRUEBA DE CONECTIVIDAD");
        connectivityTitle.setTextSize(18);
        connectivityTitle.setGravity(Gravity.START);
        connectivityTitle.setPadding(0, 28, 0, 10);
        root.addView(connectivityTitle);

        TextView connectivityInfo = new TextView(this);
        connectivityInfo.setText(
                "Prueba la conectividad de red antes de intentar una conexión P2P.\n\n" +
                "🟢 SOLO — diagnóstico de un dispositivo.\n" +
                "🟠 CREAR SALA — prueba P2P como anfitrión.\n" +
                "🔵 UNIRSE A SALA — prueba P2P como invitado.\n\n" +
                "Las pruebas P2P requieren dos móviles en redes diferentes."
        );
        connectivityInfo.setTextSize(14);
        connectivityInfo.setGravity(Gravity.START);
        connectivityInfo.setPadding(20, 8, 20, 12);
        root.addView(connectivityInfo);

        Button soloButton = new Button(this);
        soloButton.setText("🟢 SOLO");
        soloButton.setAllCaps(false);
        root.addView(soloButton);

        Button createRoomButton = new Button(this);
        createRoomButton.setText("🟠 CREAR SALA");
        createRoomButton.setAllCaps(false);
        root.addView(createRoomButton);

        Button joinRoomButton = new Button(this);
        joinRoomButton.setText("🔵 UNIRSE A SALA");
        joinRoomButton.setAllCaps(false);
        root.addView(joinRoomButton);

        soloButton.setOnClickListener(v -> {
            if (p2pRunning) {
                status.setText("⚠️ Ya hay una prueba en ejecución.");
                return;
            }

            ejecutarModoSolo();
        });

        createRoomButton.setOnClickListener(v -> {
            if (p2pRunning) {
                status.setText("⚠️ Ya hay una prueba P2P en ejecución.");
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("🟠 CREAR SALA")
                    .setMessage(
                            "Para esta prueba necesitas otro móvil.\n\n" +
                            "📱 Móvil 1: CREAR SALA\n" +
                            "📱 Móvil 2: UNIRSE A SALA\n\n" +
                            "⚠️ Ambos móviles deben estar en redes Wi-Fi diferentes.\n" +
                            "No uses Wi-Fi Direct.\n\n" +
                            "La prueba utilizará STUN, señalización WebSocket, " +
                            "intercambio de endpoints y UDP P2P."
                    )
                    .setNegativeButton("CANCELAR", null)
                    .setPositiveButton("CREAR SALA", (dialog, which) -> {
                        iniciarP2P(true, "");
                    })
                    .show();
        });

        joinRoomButton.setOnClickListener(v -> {
            if (p2pRunning) {
                status.setText("⚠️ Ya hay una prueba P2P en ejecución.");
                return;
            }

            final EditText roomInput = new EditText(this);
            roomInput.setHint("Código de sala");
            roomInput.setSingleLine(true);
            roomInput.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            );

            new AlertDialog.Builder(this)
                    .setTitle("🔵 UNIRSE A SALA")
                    .setMessage(
                            "Introduce el código que te proporcionó el otro móvil.\n\n" +
                            "⚠️ Ambos móviles deben estar en redes Wi-Fi diferentes.\n" +
                            "No uses Wi-Fi Direct."
                    )
                    .setView(roomInput)
                    .setNegativeButton("CANCELAR", null)
                    .setPositiveButton("UNIRSE", (dialog, which) -> {
                        String room = roomInput.getText()
                                .toString()
                                .trim()
                                .toUpperCase(java.util.Locale.US);

                        if (room.isEmpty()) {
                            status.setText("⚠️ Debes introducir un código de sala.");
                            return;
                        }

                        iniciarP2P(false, room);
                    })
                    .show();
        });

        TextView connectivityDetails = new TextView(this);
        connectivityDetails.setText(
                "🔬 Diagnóstico detallado:\n" +
                "STUN • NAT • UPnP • UDP P2P • latencia • pérdida • estabilidad"
        );
        connectivityDetails.setTextSize(13);
        connectivityDetails.setGravity(Gravity.START);
        connectivityDetails.setPadding(20, 12, 20, 20);
        root.addView(connectivityDetails);
        createResultPanel(root);

        TextView labTitle = new TextView(this);
        labTitle.setText("4. 🎮 LABORATORIO PS3");
        labTitle.setTextSize(18);
        labTitle.setGravity(Gravity.START);
        labTitle.setPadding(0, 28, 0, 10);
        root.addView(labTitle);

        TextView labInfo = new TextView(this);
        labInfo.setText(
                "🧪 SIMULADOR DE CONDICIONES DE RED PS3\n\n" +
                "Reproduce condiciones de conectividad relevantes para PS3 " +
                "mediante pruebas de DNS, NAT, UPnP, STUN, UDP, latencia, " +
                "pérdida y estabilidad.\n\n" +
                "⚠️ No convierte el teléfono en una PS3 ni ejecuta " +
                "protocolos propietarios del juego."
        );
        labInfo.setTextSize(14);
        labInfo.setGravity(Gravity.START);
        labInfo.setPadding(20, 8, 20, 12);
        root.addView(labInfo);

        Button labButton = new Button(this);
        labButton.setText("🎮 ABRIR LABORATORIO PS3");
        labButton.setAllCaps(false);
        root.addView(labButton);
        // ============================================================
        // 5. 🛠️ ESTADO DEL SISTEMA
        // ============================================================

        TextView systemTitle = new TextView(this);
        systemTitle.setText("5. 🛠️ ESTADO DEL SISTEMA");
        systemTitle.setTextSize(18);
        systemTitle.setGravity(Gravity.START);
        systemTitle.setPadding(0, 28, 0, 10);
        root.addView(systemTitle);

        systemStatusContainer = new LinearLayout(this);
        systemStatusContainer.setOrientation(LinearLayout.VERTICAL);
        systemStatusContainer.setPadding(20, 12, 20, 12);

        GradientDrawable systemBackground = new GradientDrawable();
        systemBackground.setColor(Color.rgb(18, 22, 30));
        systemBackground.setCornerRadius(24);
        systemBackground.setStroke(2, Color.rgb(70, 80, 95));
        systemStatusContainer.setBackground(systemBackground);

        systemSummary = new TextView(this);
        systemSummary.setText(
                "⚪ Estado aún no comprobado\n\n" +
                "Pulsa el botón para comprobar Internet, DNS,\n" +
                "PSN y los servicios de conectividad disponibles."
        );
        systemSummary.setTextSize(14);
        systemSummary.setTextColor(Color.WHITE);
        systemSummary.setGravity(Gravity.START);
        systemSummary.setPadding(16, 16, 16, 16);

        systemStatusContainer.addView(systemSummary);
        root.addView(systemStatusContainer);

        Button systemCheckButton = new Button(this);
        systemCheckButton.setText("🔍 COMPROBAR ESTADO DEL SISTEMA");
        systemCheckButton.setAllCaps(false);
        root.addView(systemCheckButton);

        systemCheckButton.setOnClickListener(v -> {
            if (systemStatusRunning) {
                return;
            }

            systemStatusRunning = true;
            systemCheckButton.setEnabled(false);

            systemSummary.setText(
                    "🟡 COMPROBANDO...\n\n" +
                    "🌐 Internet\n" +
                    "🔎 DNS\n" +
                    "🎮 PSN\n" +
                    "🔌 Conectividad"
            );

            SystemStatusService service = new SystemStatusService();

            service.checkAll(this, items -> {
                runOnUiThread(() -> {

                    StringBuilder result = new StringBuilder();

                    int ok = 0;
                    int degraded = 0;
                    int error = 0;
                    int unknown = 0;

                    for (SystemStatusService.Item item : items) {

                        String icon;

                        switch (item.state) {
                            case OK:
                                icon = "🟢";
                                ok++;
                                break;

                            case DEGRADED:
                                icon = "🟡";
                                degraded++;
                                break;

                            case ERROR:
                                icon = "🔴";
                                error++;
                                break;

                            default:
                                icon = "⚪";
                                unknown++;
                                break;
                        }

                        result.append(icon)
                                .append(" ")
                                .append(item.name)
                                .append("\n")
                                .append(item.description)
                                .append(" • ")
                                .append(item.detail)
                                .append("\n\n");
                    }

                    result.append("━━━━━━━━━━━━━━━━━━━━\n")
                            .append("📊 RESUMEN\n")
                            .append("🟢 Correctos: ").append(ok).append("\n")
                            .append("🟡 Degradados: ").append(degraded).append("\n")
                            .append("🔴 Errores: ").append(error).append("\n");

                    if (unknown > 0) {
                        result.append("⚪ Desconocidos: ")
                                .append(unknown)
                                .append("\n");
                    }

                    systemSummary.setText(result.toString());

                    systemStatusRunning = false;
                    systemCheckButton.setEnabled(true);
                });
            });

        });

        TextView systemInfo = new TextView(this);
        systemInfo.setText(
                "ℹ️ Este diagnóstico comprueba el estado general de " +
                "la conexión. Los resultados pueden variar según la " +
                "red, el proveedor y los servicios externos."
        );
        systemInfo.setTextSize(12);
        systemInfo.setGravity(Gravity.START);
        systemInfo.setPadding(20, 8, 20, 12);
        root.addView(systemInfo);
        TextView communityTitle = new TextView(this);
        communityTitle.setText("6. 👥 COMUNIDADES");
        communityTitle.setTextSize(18);
        communityTitle.setGravity(Gravity.START);
        communityTitle.setPadding(0, 28, 0, 10);
        root.addView(communityTitle);

        communityInfo = new TextView(this);
        communityInfo.setText(
                "Comunidades disponibles para registro, " +
                "verificación y soporte de juegos.\n\n" +
                "💬 PS3 Online Assistant\n" +
                "🎮 GTA ReV\n" +
                "🚗 Rocket League & MK9\n" +
                "🔫 Battlefield\n" +
                "🏎️ Gran Turismo 5 & 6\n" +
                "🔧 Counter-Strike: GO\n" +
                "🆔 The Last of Us + Uncharted 2 + Uncharted 3"
        );
        communityInfo.setTextSize(15);
        communityInfo.setGravity(Gravity.START);
        communityInfo.setPadding(20, 12, 20, 12);
        root.addView(communityInfo);

        communityButton = new Button(this);
        communityButton.setText("👥 ABRIR ENLACES DE COMUNIDAD");
        communityButton.setAllCaps(false);
        root.addView(communityButton);

        // Inicializa la tarjeta y el botón de comunidad.
        updateCommunityCard();

        TextView faqTitle = new TextView(this);
        faqTitle.setText("7. ❓ FAQ");
        faqTitle.setTextSize(18);
        faqTitle.setGravity(Gravity.START);
        faqTitle.setPadding(0, 28, 0, 10);
        root.addView(faqTitle);

        TextView faqInfo = new TextView(this);
        faqInfo.setText(
                "🌐 ¿Qué es la prueba STUN?\n\n" +
                "STUN permite conocer la IP y el puerto público observados " +
                "desde Internet. Esta información ayuda a evaluar la " +
                "conectividad P2P junto con NAT, UPnP y UDP.\n\n" +
                "⚠️ Que STUN responda no significa automáticamente que el " +
                "NAT esté abierto ni garantiza que un juego de PS3 vaya a conectar."
        );
        faqInfo.setTextSize(14);
        faqInfo.setGravity(Gravity.START);
        faqInfo.setPadding(20, 8, 20, 20);
        root.addView(faqInfo);

        TextView supportTitle = new TextView(this);
        supportTitle.setText("8. ☕ APOYAR EL PROYECTO");
        supportTitle.setTextSize(18);
        supportTitle.setGravity(Gravity.START);
        supportTitle.setPadding(0, 28, 0, 10);
        root.addView(supportTitle);

        TextView supportInfo = new TextView(this);
        supportInfo.setText(
                "El apoyo es completamente voluntario.\n" +
                "Ayuda a mantener la APK, la API y la base de datos."
        );
        supportInfo.setTextSize(14);
        supportInfo.setGravity(Gravity.START);
        supportInfo.setPadding(20, 8, 20, 18);
        root.addView(supportInfo);

        Button supportButton = new Button(this);
        supportButton.setText("☕ APOYAR EL PROYECTO");
        supportButton.setAllCaps(false);
        root.addView(supportButton);

        TextView infoTitle = new TextView(this);
        infoTitle.setText("9. ℹ️ INFORMACIÓN");
        infoTitle.setTextSize(18);
        infoTitle.setGravity(Gravity.START);
        infoTitle.setPadding(0, 28, 0, 10);
        root.addView(infoTitle);

        TextView infoText = new TextView(this);
        infoText.setText(
                "PS3 ONLINE DNS TEST\n" +
                "Diagnóstico DNS, conectividad P2P y laboratorio experimental."
        );
        infoText.setTextSize(14);
        infoText.setGravity(Gravity.START);
        infoText.setPadding(20, 8, 20, 20);
        root.addView(infoText);

        status = new TextView(this);
        status.setText(
                "\nEstado: esperando prueba."
        );
        status.setTextSize(14);
        status.setGravity(Gravity.START);
        status.setTextIsSelectable(true);
        status.setPadding(20, 20, 20, 24);

        GradientDrawable statusBackground = new GradientDrawable();
        statusBackground.setColor(Color.rgb(18, 22, 30));
        statusBackground.setCornerRadius(24);
        statusBackground.setStroke(2, Color.rgb(70, 80, 95));
        status.setBackground(statusBackground);

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        statusParams.setMargins(0, 12, 0, 24);

        root.addView(status, statusParams);


gameSelector.setOnItemClickListener(
        (parent, view, position, id) -> {

            String selectedName =
                    gameSelector.getText().toString().trim();

            /*
             * Primero buscamos en la base local.
             * Los datos registrados siempre tienen prioridad.
             */
            GameProfile game = null;

            for (GameProfile candidate : games) {

                if (candidate.nombre != null &&
                        candidate.nombre.equalsIgnoreCase(
                                selectedName
                        )) {

                    game = candidate;
                    break;
                }
            }

            /*
             * JUEGO NO REGISTRADO:
             * puede proceder de Wikidata.
             *
             * No inventamos DNS, PSN, comunidad ni
             * requisitos para un resultado externo.
             */
            if (game == null) {
                    selectedGameProfile = null;
                    selectedCommunities.clear();
                    updateCommunityCard();

                selectedDnsCombos.clear();

                dnsSelector.setText("", false);
                customDns.setText("");

                dnsStatus.setText(
                        "🌐 JUEGO ENCONTRADO EN INTERNET\n\n" +
                        "🎮 " + selectedName +
                        "\n" +
                        "Plataforma: PlayStation 3\n\n" +
                        "⚠️ Este juego todavía no está " +
                        "registrado en la base local.\n\n" +
                        "No se asignará automáticamente una DNS " +
                        "ni un estado PSN."
                );

                gameInfo.setText(
                        "🌐 RESULTADO EXTERNO\n" +
                        "🎮 " + selectedName +
                        "\n" +
                        "PlayStation 3\n\n" +
                        "Para obtener DNS, estado PSN o información " +
                        "de comunidad, primero debe verificarse y " +
                        "registrarse."
                );

                verificationCard.setText(
                        "⚠️ VERIFICACIÓN RECOMENDADA\n\n" +
                        "Este resultado procede de una búsqueda " +
                        "externa y todavía no forma parte de la " +
                        "base verificada de PS3 ONLINE DNS TEST."
                );

                verificationCard.setVisibility(
                        View.VISIBLE
                );

                requirementCard.setVisibility(
                        View.GONE
                );

                return;
            }

            /*
             * JUEGO REGISTRADO:
             * conservamos el comportamiento original.
             */
            selectedGameProfile = game;
            selectedDnsCombos.clear();

            // Reúne todas las comunidades asociadas al juego.
            selectedCommunities.clear();
            selectedCommunities.addAll(
                    resolveCommunities(
                            selectedGameProfile,
                            null
                    )
            );

            updateCommunityCard();

            for (String comboId : game.dnsComboIds) {

                for (DnsCombo combo : dnsCombos) {

                    if (combo.id.equals(comboId)) {

                        selectedDnsCombos.add(combo);
                        break;
                    }
                }
            }

            dnsSelector.setText("", false);
            customDns.setText("");

            if (!selectedDnsCombos.isEmpty()) {

                DnsCombo combo =
                        selectedDnsCombos.get(0);

                if (combo.dnsPrimaria != null &&
                        !combo.dnsPrimaria.trim().isEmpty()) {

                    dnsSelector.setText(
                            combo.dnsPrimaria,
                            false
                    );
                }

                if (combo.dnsSecundaria != null &&
                        !combo.dnsSecundaria.trim().isEmpty()) {

                    customDns.setText(
                            combo.dnsSecundaria
                    );
                }

                dnsStatus.setText(
                        "🔵 DNS REGISTRADA\n" +
                        combo.dnsPrimaria +
                        "\nSecundaria: " +
                        (
                                combo.dnsSecundaria == null
                                        ? "No registrada"
                                        : combo.dnsSecundaria
                        )
                );

            } else {

                dnsStatus.setText(
                        "⚪ Este juego no tiene todavía " +
                        "una DNS registrada."
                );
            }

            /*
             * Estado PSN de la base local.
             */
            if (game.psnActivo) {

                gameInfo.setText(
                        "FICHA: " + game.nombre +
                        "\n🟢 PSN ACTIVO"
                );

            } else {

                gameInfo.setText(
                        "FICHA: " + game.nombre
                );
            }

            /*
             * Verificación de comunidad.
             */
            if (game.requiereVerificacion) {

                CommunityRegistry.CommunityInfo verificationCommunity =
                        resolveCommunity(game, null);

                String communityName =
                        verificationCommunity != null
                                ? verificationCommunity.name
                                : "PS3 Online y LAN";

                verificationCard.setText(
                        "⚠️ VERIFICACIÓN EN COMUNIDAD REQUERIDA\n\n" +
                        "Este juego no se marca como PSN ACTIVO.\n\n" +
                        "👥 COMUNIDAD: " + communityName + "\n\n" +
                        "La disponibilidad debe comprobarse en la " +
                        "comunidad correspondiente."
                );

                verificationCard.setVisibility(
                        View.VISIBLE
                );

            } else {

                verificationCard.setVisibility(
                        View.GONE
                );
            }

            /*
             * MOD / PKG / comandos.
             */
            if (game.modPkgRequisito != null ||
                    game.versionRequisito != null ||
                    game.comandosConsola != null) {

                StringBuilder req =
                        new StringBuilder();

                req.append(
                        "📦 REQUIERE MOD / PKG / " +
                        "COMANDOS EN CONSOLA IN-GAME"
                );

                if (game.modPkgRequisito != null) {

                    req.append("\nMOD/PKG: ")
                       .append(game.modPkgRequisito);
                }

                if (game.versionRequisito != null) {

                    req.append("\nVersión: ")
                       .append(game.versionRequisito);
                }

                if (game.comandosConsola != null) {

                    req.append("\nComandos: ")
                       .append(game.comandosConsola);
                }

                requirementCard.setText(
                        req.toString()
                );

                requirementCard.setVisibility(
                        View.VISIBLE
                );

            } else {

                requirementCard.setVisibility(
                        View.GONE
                );
            }
        }
);
    setContentView(mainScroll);

        onlinePresenceService =
                new OnlinePresenceService(
                        new OnlinePresenceService.Listener() {

                            @Override
                            public void onOnlineCountChanged(long count) {
                                if (onlineUsers != null) {
                                    onlineUsers.setText(
                                            "👥 " + count + " online"
                                    );
                                }
                            }

                            @Override
                            public void onPresenceError(String message) {
                                if (onlineUsers != null) {
                                    onlineUsers.setText(
                                            "👥 — online"
                                    );
                                }
                            }
                        }
                );

    }

    private void ejecutarModoSolo() {

        final String primary = dnsSelector == null
                ? ""
                : dnsSelector.getText().toString().trim();

        final String secondary = customDns == null
                ? ""
                : customDns.getText().toString().trim();

        if (primary.isEmpty() && secondary.isEmpty()) {
            status.setText(
                    "⚠️ MODO SOLO\n\n" +
                    "Introduce al menos una DNS para realizar la prueba."
            );
            return;
        }

        p2pRunning = true;

        status.setText(
                "🟢 MODO SOLO\n\n" +
                "🔎 Iniciando diagnóstico...\n" +
                "🌐 DNS\n" +
                "📡 STUN\n" +
                "⏱️ Latencia\n\n" +
                "ℹ️ No se abrirá WebSocket ni sala P2P."
        );

        dnsExecutor.execute(() -> {

            DnsTester.Result primaryResult = null;
            DnsTester.Result secondaryResult = null;
            StunP2P.Result stunResult = null;

            StringBuilder result = new StringBuilder();

            try {

                if (!primary.isEmpty()) {

                    DnsTester.Result finalPrimary =
                            DnsTester.queryA(
                                    primary,
                                    "example.com",
                                    DnsTester.DEFAULT_TIMEOUT_MS
                            );

                    primaryResult = finalPrimary;
                }

                if (!secondary.isEmpty()) {

                    DnsTester.Result finalSecondary =
                            DnsTester.queryA(
                                    secondary,
                                    "example.com",
                                    DnsTester.DEFAULT_TIMEOUT_MS
                            );

                    secondaryResult = finalSecondary;
                }

                try {

                    stunResult =
                            StunP2P.discover(
                                    "stun.cloudflare.com",
                                    3478,
                                    6000
                            );

                } catch (Exception e) {

                    stunResult =
                            new StunP2P.Result(
                                    false,
                                    "STUN: " +
                                            (
                                                    e.getMessage() == null
                                                            ? e.getClass().getSimpleName()
                                                            : e.getMessage()
                                            ),
                                    null,
                                    null
                            );
                }

                result.append("🟢 MODO SOLO — RESULTADO\n\n");

                result.append("🌐 DNS PRIMARIA\n");

                if (primaryResult != null) {
                    result.append(
                            primaryResult.ok
                                    ? "🟢 RESPONDE"
                                    : "🔴 NO CONFIRMADA"
                    );

                    result.append(
                            " • "
                                    + primaryResult.rttMs
                                    + " ms\n"
                    );

                    result.append(
                            "   "
                                    + primaryResult.message
                                    + "\n"
                    );

                } else {
                    result.append("⚪ NO CONFIGURADA\n");
                }

                result.append("\n🌐 DNS SECUNDARIA\n");

                if (secondaryResult != null) {
                    result.append(
                            secondaryResult.ok
                                    ? "🟢 RESPONDE"
                                    : "🔴 NO CONFIRMADA"
                    );

                    result.append(
                            " • "
                                    + secondaryResult.rttMs
                                    + " ms\n"
                    );

                    result.append(
                            "   "
                                    + secondaryResult.message
                                    + "\n"
                    );

                } else {
                    result.append("⚪ NO CONFIGURADA\n");
                }

                result.append("\n📡 STUN\n");

                if (
                        stunResult != null
                                && stunResult.ok
                                && stunResult.mapped != null
                ) {

                    result.append("🟢 RESPONDE\n");
                    result.append(
                            "🌐 Endpoint observado: "
                                    + stunResult.mapped.getAddress()
                                            .getHostAddress()
                                    + ":"
                                    + stunResult.mapped.getPort()
                                    + "\n"
                    );

                    result.append(
                            "ℹ️ Esto confirma que STUN observó un endpoint público.\n"
                    );

                } else {

                    result.append(
                            "🟡 NO SE PUDO CONFIRMAR\n"
                    );

                    if (
                            stunResult != null
                                    && stunResult.message != null
                    ) {
                        result.append(
                                "   "
                                        + stunResult.message
                                        + "\n"
                        );
                    }
                }

                result.append("\n📋 INTERPRETACIÓN\n");

                boolean dnsOk =
                        (primaryResult != null && primaryResult.ok)
                                || (secondaryResult != null && secondaryResult.ok);

                if (dnsOk) {
                    result.append(
                            "🟢 DNS: al menos un resolver respondió correctamente.\n"
                    );
                } else {
                    result.append(
                            "🔴 DNS: no se confirmó una respuesta válida.\n"
                    );
                }

                if (stunResult != null && stunResult.ok) {
                    result.append(
                            "🟢 STUN: endpoint público observado.\n"
                    );
                } else {
                    result.append(
                            "🟡 STUN: no se pudo confirmar.\n"
                    );
                }

                result.append(
                        "\n⚠️ Este diagnóstico no determina por sí solo " +
                        "si un juego PS3 podrá establecer una conexión P2P."
                );

            } finally {

                if (
                        stunResult != null
                                && stunResult.socket != null
                ) {
                    try {
                        stunResult.socket.close();
                    } catch (Exception ignored) {
                    }
                }
            }

            final String finalResult = result.toString();

            runOnUiThread(() -> {
                status.setText(finalResult);
                p2pRunning = false;
            });
        });
    }

    private void iniciarP2P(boolean host, String room) {

        if (p2pRunning) {
            status.setText("⚠️ Ya existe una prueba P2P en ejecución.");
            return;
        }

        String dns = "";
        if (dnsSelector != null) {
            dns = dnsSelector.getText()
                    .toString()
                    .trim();
        }

        String game = "";
        if (gameSelector != null) {
            game = gameSelector.getText()
                    .toString()
                    .trim();
        }

        if (game.isEmpty()) {
            game = "PS3 / PSN general";
        }

        p2pRunning = true;
        p2pRoomCode = room == null
                ? ""
                : room.trim().toUpperCase(java.util.Locale.US);

        String roleInfo = host
                ? "🟠 ANFITRIÓN"
                : "🔵 INVITADO";

        status.setText(
                "🧪 INICIANDO PRUEBA P2P\n\n" +
                roleInfo + "\n" +
                "🎮 Juego: " + game + "\n" +
                "🌐 DNS: " + (dns.isEmpty() ? "automática" : dns) + "\n\n" +
                "🔎 Preparando STUN..."
        );

        final String finalDns = dns;
        final String finalGame = game;
        final boolean finalHost = host;
        final String finalRoom = p2pRoomCode;

        p2pSession = new P2PSession(
                finalHost,
                finalRoom,
                finalDns,
                "Juego: " + finalGame,
                this,
                message -> runOnUiThread(() -> {

                    if (message == null) {
                        return;
                    }

                    String text = message.trim();

                    if (text.startsWith("🏠 SALA:")) {
                        String detectedRoom = text
                                .substring("🏠 SALA:".length())
                                .trim();

                        if (!detectedRoom.isEmpty()) {
                            p2pRoomCode = detectedRoom;
                        }
                    }

                    status.setText(text);

                    if (
                            text.contains("🔴 WEBSOCKET") ||
                            text.contains("🔴 SEÑAL:") ||
                            text.contains("🔴 P2P") ||
                            text.contains("🔴 STUN") ||
                            text.contains("⚠️ STUN")
                    ) {
                        p2pRunning = false;
                    }
                })
        );

        p2pSession.start();
    }

    private void testDnsSimultaneously() {

        final String primary =
                dnsSelector.getText().toString().trim();

        final String secondary =
                customDns.getText().toString().trim();

        if (primary.isEmpty() || secondary.isEmpty()) {
            status.setText(
                    "⚠️ Introduce DNS primaria y DNS secundaria."
            );
            return;
        }

        if (!isValidIpv4(primary) || !isValidIpv4(secondary)) {
            status.setText(
                    "⚠️ Una de las DNS no tiene una dirección IPv4 válida."
            );
            return;
        }

        testButton.setEnabled(false);

        status.setText(
                "⏳ Probando DNS primaria y secundaria " +
                "simultáneamente..."
        );

        dnsExecutor.execute(() -> {

            final DnsTester.Result[] results =
                    new DnsTester.Result[2];

            Thread primaryThread = new Thread(() -> {
                results[0] =
                        DnsTester.queryA(
                                primary,
                                "example.com",
                                3000
                        );
            });

            Thread secondaryThread = new Thread(() -> {
                results[1] =
                        DnsTester.queryA(
                                secondary,
                                "example.com",
                                3000
                        );
            });

            long start = System.currentTimeMillis();

            primaryThread.start();
            secondaryThread.start();

            try {
                primaryThread.join();
                secondaryThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            long total =
                    System.currentTimeMillis() - start;

            runOnUiThread(() -> {

                testButton.setEnabled(true);

                showDnsResult(
                        primary,
                        secondary,
                        results[0],
                        results[1],
                        total
                );
            });
        });
    }

    private void createResultPanel(LinearLayout root) {
        resultPanel = new LinearLayout(this);
        resultPanel.setOrientation(LinearLayout.VERTICAL);
        resultPanel.setPadding(20, 20, 20, 20);

        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(Color.rgb(18, 22, 30));
        panelBackground.setCornerRadius(28);
        panelBackground.setStroke(2, Color.rgb(70, 80, 95));
        resultPanel.setBackground(panelBackground);

        LinearLayout.LayoutParams panelParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        panelParams.setMargins(0, 24, 0, 24);

        resultHeader = new TextView(this);
        resultHeader.setText("🧪 RESULTADO DE LA PRUEBA");
        resultHeader.setTextSize(18);
        resultHeader.setTextColor(Color.WHITE);
        resultHeader.setGravity(Gravity.START);
        resultHeader.setPadding(0, 0, 0, 12);
        resultPanel.addView(resultHeader);

        resultStatus = new TextView(this);
        resultStatus.setText("⚪ Esperando una prueba...");
        resultStatus.setTextSize(14);
        resultStatus.setTextColor(Color.WHITE);
        resultStatus.setGravity(Gravity.START);
        resultStatus.setTextIsSelectable(true);
        resultStatus.setPadding(4, 8, 4, 12);
        resultPanel.addView(resultStatus);

        LinearLayout resultButtons = new LinearLayout(this);
        resultButtons.setOrientation(LinearLayout.HORIZONTAL);

        resultCopyButton = new Button(this);
        resultCopyButton.setText("📋 COPIAR");
        resultCopyButton.setAllCaps(false);

        resultRepeatButton = new Button(this);
        resultRepeatButton.setText("🔄 REPETIR");
        resultRepeatButton.setAllCaps(false);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );
        buttonParams.setMargins(4, 8, 4, 0);

        resultButtons.addView(resultCopyButton, buttonParams);
        resultButtons.addView(resultRepeatButton, buttonParams);

        resultPanel.addView(resultButtons);

        resultCopyButton.setOnClickListener(v -> {
            if (lastResultText == null || lastResultText.isEmpty()) {
                return;
            }

            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);

            if (clipboard != null) {
                clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                                "Resultado PS3 ONLINE DNS TEST",
                                lastResultText
                        )
                );
            }
        });

        resultRepeatButton.setOnClickListener(v -> {
            if (!p2pRunning) {
                testDnsSimultaneously();
            }
        });

        resultPanel.setVisibility(View.GONE);
        root.addView(resultPanel, panelParams);
    }

    private void showDnsResult(
            String primary,
            String secondary,
            DnsTester.Result primaryResult,
            DnsTester.Result secondaryResult,
            long totalMs) {

        boolean primaryOk =
                primaryResult != null && primaryResult.ok;

        boolean secondaryOk =
                secondaryResult != null && secondaryResult.ok;

        /*
         * FASE 4:
         * El resultado DNS ya no decide por sí solo
         * que una configuración esté "lista para jugar".
         *
         * El DNS solamente aporta evidencia DNS al
         * ResultInterpreter. Las pruebas de PSN,
         * servicio del juego y P2P se incorporarán
         * posteriormente cuando exista evidencia real.
         */

        TestResult dnsResult;

        if (primaryOk || secondaryOk) {
            dnsResult = TestResult.DNS_OK;
        } else if (primaryResult != null || secondaryResult != null) {
            dnsResult = TestResult.DNS_FAIL;
        } else {
            dnsResult = TestResult.DNS_UNKNOWN;
        }

        ResultInterpreter.DiagnosticInput input =
                new ResultInterpreter.DiagnosticInput();

        input.gameName =
                gameSelector == null
                        ? ""
                        : gameSelector.getText().toString().trim();

        input.primaryDns = primary;
        input.secondaryDns = secondary;

        input.dns = dnsResult;

        /*
         * Todavía no se ha ejecutado una prueba real
         * específica del servicio del juego en este flujo.
         *
         * Por eso permanece UNKNOWN.
         *
         * Esto es intencional: UNKNOWN nunca debe
         * convertirse artificialmente en OK.
         */
        input.gameService =
                TestResult.GAME_SERVICE_UNKNOWN;

        /*
         * Este flujo tampoco realiza una prueba PSN
         * independiente.
         */
        /*
         * La ficha de la biblioteca indica si el juego está marcado
         * como PSN activo, pero eso NO constituye una prueba real
         * de conectividad PSN.
         *
         * Por eso la prueba permanece UNKNOWN hasta disponer de
         * evidencia de red independiente.
         */
        input.psn = TestResult.PSN_UNKNOWN;

        /*
         * The Last of Us (id=tlou) es una excepción definida en
         * la biblioteca: DNS + verificación, sin requisito PSN.
         *
         * Para juegos registrados normalmente, psnRequired queda
         * activo cuando la ficha pertenece al sistema PS3 ONLINE.
         * Para juegos externos no exigimos PSN porque no tenemos
         * metadatos locales suficientes.
         */
        boolean hasLocalGame = selectedGameProfile != null;

        boolean isTheLastOfUs =
                hasLocalGame &&
                selectedGameProfile.id != null &&
                "tlou".equalsIgnoreCase(
                        selectedGameProfile.id.trim()
                );

        input.psnRequired =
                hasLocalGame &&
                selectedGameProfile.psnActivo &&
                !isTheLastOfUs;

        /*
         * STUN/P2P/UPnP no se ejecutan dentro de
         * testDnsSimultaneously(), por lo que permanecen
         * desconocidos.
         */
        input.stun = TestResult.STUN_UNKNOWN;
        input.upnp = TestResult.UPNP_UNKNOWN;
        input.p2p = TestResult.P2P_UNKNOWN;
        input.latency = TestResult.LATENCY_UNKNOWN;
        input.loss = TestResult.LOSS_UNKNOWN;

        ResultInterpreter.Interpretation interpretation =
                ResultInterpreter.interpret(input);

        StringBuilder result =
                new StringBuilder();

        result.append("⚡ RESULTADO DEL ANÁLISIS\n\n");

        result.append(interpretation.title)
                .append("\n\n");

        result.append(interpretation.summary)
                .append("\n\n");

        result.append("━━━━━━━━━━━━━━━━━━━━\n");
        result.append("🌐 EVIDENCIA DNS\n\n");

        result.append("DNS PRIMARIA\n");

        if (primaryResult != null) {

            if (primaryResult.ok) {
                result.append("🟢 RESPONDE\n");
            } else if (primaryResult.message != null
                    && primaryResult.message.contains("RCODE=5")) {

                result.append(
                        "🟠 RESPONDE PERO RECHAZA LA CONSULTA\n"
                );

            } else {
                result.append("🔴 SIN RESPUESTA VÁLIDA\n");
            }

            result.append("IP: ")
                    .append(primary)
                    .append("\n");

            result.append("Tiempo: ")
                    .append(primaryResult.rttMs)
                    .append(" ms\n");

            result.append(primaryResult.message)
                    .append("\n");

        } else {

            result.append("⚪ NO CONFIGURADA\n");
        }

        result.append("\nDNS SECUNDARIA\n");

        if (secondaryResult != null) {

            if (secondaryResult.ok) {
                result.append("🟢 RESPONDE\n");

            } else if (secondaryResult.message != null
                    && secondaryResult.message.contains("RCODE=5")) {

                result.append(
                        "🟠 RESPONDE PERO RECHAZA LA CONSULTA\n"
                );

            } else {
                result.append("🔴 SIN RESPUESTA VÁLIDA\n");
            }

            result.append("IP: ")
                    .append(secondary)
                    .append("\n");

            result.append("Tiempo: ")
                    .append(secondaryResult.rttMs)
                    .append(" ms\n");

            result.append(secondaryResult.message)
                    .append("\n");

        } else {

            result.append("⚪ NO CONFIGURADA\n");
        }

        result.append("\nTiempo total: ")
                .append(totalMs)
                .append(" ms\n");

        result.append("\n━━━━━━━━━━━━━━━━━━━━\n");
        result.append("📋 INTERPRETACIÓN\n\n");

        result.append(interpretation.communityMessage)
                .append("\n\n");

        result.append("🔬 TÉCNICO\n")
                .append(interpretation.technicalMessage)
                .append("\n\n");

        if (!interpretation.observations.isEmpty()) {

            result.append("🔎 OBSERVACIONES\n");

            for (String observation :
                    interpretation.observations) {

                result.append("• ")
                        .append(observation)
                        .append("\n");
            }

            result.append("\n");
        }

        if (!interpretation.recommendations.isEmpty()) {

            result.append("🛠️ SIGUIENTE PASO\n");

            for (String recommendation :
                    interpretation.recommendations) {

                result.append("• ")
                        .append(recommendation)
                        .append("\n");
            }

            result.append("\n");
        }

        result.append("━━━━━━━━━━━━━━━━━━━━\n");
        result.append("Consulta real: DNS A / UDP 53\n\n");

        result.append(
                "⚠️ DNS funcional no significa por sí solo "
                + "compatibilidad con un juego PS3."
        );

        lastResultText = result.toString();

        status.setText(lastResultText);

        if (resultPanel != null && resultStatus != null) {
            resultPanel.setVisibility(View.VISIBLE);
            resultStatus.setText(lastResultText);

            resultHeader.setText("🧪 RESULTADO DEL ANÁLISIS");

            if (primaryOk && secondaryOk) {
                resultHeader.setText("🟢 RESULTADO DEL ANÁLISIS");
            } else if (primaryOk || secondaryOk) {
                resultHeader.setText("🟡 RESULTADO DEL ANÁLISIS");
            } else {
                resultHeader.setText("🔴 RESULTADO DEL ANÁLISIS");
            }

            resultCopyButton.setEnabled(true);
            resultRepeatButton.setEnabled(!p2pRunning);

            if (mainScroll != null) {
                mainScroll.postDelayed(() ->
                        mainScroll.smoothScrollTo(
                                0,
                                resultPanel.getBottom()
                        ), 150);
            }
        }
    }

    private boolean isValidIpv4(String ip) {

        String[] parts = ip.split("\\.");

        if (parts.length != 4) {
            return false;
        }

        try {

            for (String part : parts) {

                int value = Integer.parseInt(part);

                if (value < 0 || value > 255) {
                    return false;
                }
            }

            return true;

        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (onlinePresenceService != null) {
            onlinePresenceService.start();
        }
    }

    @Override
    protected void onStop() {
        if (onlinePresenceService != null) {
            onlinePresenceService.stop();
        }

        super.onStop();
    }

}
