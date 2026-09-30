package com.ps3online.dnstester.model;

import java.util.ArrayList;
import java.util.List;

public class DnsCombo {

    public enum Origen {
        REGISTRADA,
        SUGERIDA_API,
        PERSONALIZADA
    }

    public enum Estado {
        AZUL_OFICIAL,
        AZUL_COMUNIDAD,
        AMARILLO_SUGERIDA,
        ROJO_CAIDA
    }

    public String id;

    /*
     * Un combo puede estar asociado a un juego concreto
     * o a un grupo de juegos.
     */
    public String juegoId;
    public String grupoId;

    public String dnsPrimaria;
    public String dnsSecundaria;

    /*
     * Alternativas/fallback.
     */
    public List<String> secundariasAlternativas = new ArrayList<>();

    public int votosPositivos;
    public int votosNegativos;

    public Origen origen;
    public Estado estado;

    /*
     * Información humana para la interfaz.
     */
    public String fuente;
    public String nota;

    /*
     * Si la secundaria debe proceder de un DNS público
     * elegido por el usuario.
     */
    public boolean secundariaPublica = false;

    /*
     * Si este combo es solamente un fallback.
     */
    public boolean fallback = false;

    public DnsCombo() {
    }

    public DnsCombo(
            String id,
            String juegoId,
            String primaria,
            String secundaria,
            int positivos,
            int negativos,
            Origen origen,
            Estado estado
    ) {
        this.id = id;
        this.juegoId = juegoId;
        this.dnsPrimaria = primaria;
        this.dnsSecundaria = secundaria;
        this.votosPositivos = positivos;
        this.votosNegativos = negativos;
        this.origen = origen;
        this.estado = estado;
    }
}
