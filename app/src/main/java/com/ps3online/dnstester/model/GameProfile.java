package com.ps3online.dnstester.model;

import java.util.ArrayList;
import java.util.List;

public class GameProfile {

    public String id;
    public String nombre;

    /*
     * Nombres alternativos para búsqueda:
     * abreviaturas, nombres antiguos, nombres usados
     * por comunidades, etc.
     */
    public List<String> alias = new ArrayList<>();

    public String genero;

    /*
     * PSN ACTIVO solo cuando existe confirmación real.
     */
    public boolean psnActivo = false;

    /*
     * Juegos que requieren registro/verificación comunitaria.
     */
    public boolean requiereVerificacion = false;

    /*
     * Relación con perfiles DNS registrados.
     */
    public List<String> dnsComboIds = new ArrayList<>();

    /*
     * Comunidad asociada.
     */
    public String comunidadId;

    /*
     * Requisitos especiales.
     */
    public String modPkgRequisito;
    public String versionRequisito;
    public String comandosConsola;

    /*
     * Información adicional.
     */
    public String servidor;
    public String instrucciones;
    public String notas;

    public GameProfile() {
    }


}