package com.ps3online.dnstester.interpretation;

/**
 * Estados normalizados utilizados por el motor de interpretación.
 *
 * La idea es que los módulos técnicos de la APK
 * no tengan que decidir cómo explicar el resultado
 * al usuario.
 */
public enum TestResult {

    // DNS
    DNS_OK,
    DNS_FAIL,
    DNS_UNKNOWN,

    // PSN
    PSN_OK,
    PSN_FAIL,
    PSN_UNKNOWN,

    // Servicio específico del juego
    GAME_SERVICE_OK,
    GAME_SERVICE_FAIL,
    GAME_SERVICE_UNKNOWN,

    // STUN / conectividad externa
    STUN_OK,
    STUN_FAIL,
    STUN_UNKNOWN,

    // UPnP
    UPNP_OK,
    UPNP_FAIL,
    UPNP_UNKNOWN,

    // P2P
    P2P_BIDIRECTIONAL_OK,
    P2P_BIDIRECTIONAL_LOSS,
    P2P_ONE_WAY,
    P2P_NO_TRAFFIC,
    P2P_UNKNOWN,

    // Datos auxiliares
    LATENCY_GOOD,
    LATENCY_MEDIUM,
    LATENCY_HIGH,
    LATENCY_UNKNOWN,

    LOSS_NONE,
    LOSS_LOW,
    LOSS_MEDIUM,
    LOSS_HIGH,
    LOSS_UNKNOWN
}
