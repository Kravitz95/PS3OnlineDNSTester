# PS3 Online DNS Tester 3.1

APK Android para analizar una configuración de red antes de probarla en PS3.

## Qué prueba

### Modo SOLO
- Consulta DNS directa contra el servidor DNS escrito por el usuario (UDP/53).
- STUN para obtener el endpoint público y aportar evidencia sobre NAT.
- UPnP IGD: descubrimiento, mapeo UDP temporal, consulta de lease, renovación y eliminación.
- Muestra los puertos TCP/UDP documentados para el juego seleccionado.
- El resultado usa `CANDIDATA PARA PROBAR EN PS3`; no afirma compatibilidad con un juego.

### Modo DOS MÓVILES
- Teléfono A crea una sala y teléfono B entra con el código.
- Ambos obtienen su endpoint público mediante STUN.
- Un servidor WebSocket solo hace señalización.
- Los teléfonos intentan tráfico UDP directo entre sí.
- Se cuentan paquetes enviados/recibidos.
- Si no hay tráfico recibido, se informa `NO DETERMINADO`, no `PUERTO CERRADO`.

## Qué poner después en la PS3

La app muestra el DNS probado. Ese es el dato que puedes probar manualmente en la PS3.

Como punto de partida de prueba de red:
- Dirección IP: Automática
- Máscara: Automática
- Gateway: Automático
- DNS: Manual solo si quieres probar el DNS que mostró la app
- MTU: Automático
- Proxy: No usar
- UPnP: activado si el router ofrece esa opción

Los puertos documentados que aparecen en la app son principalmente datos de diagnóstico y de configuración del router cuando sea necesario. No se deben inventar puertos que no estén documentados.

## IMPORTANTE SOBRE TCP Y UDP

Un puerto TCP o UDP documentado no se convierte automáticamente en una prueba PASS solo porque un teléfono pueda enviar un paquete. UDP puede no responder aunque exista conectividad. TCP sí permite una prueba de conexión cuando existe un servidor real escuchando en el destino correcto.

La prueba móvil↔móvil es evidencia de P2P UDP entre esas dos redes, pero no reproduce automáticamente el protocolo interno de cada juego PS3.

## Compilar APK gratis con GitHub Actions

El proyecto ya incluye `.github/workflows/build-apk.yml`. No necesitas instalar Android Studio ni Gradle en tu teléfono.

1. Descarga el ZIP del proyecto.
2. Descomprímelo.
3. Entra a GitHub y crea un repositorio nuevo, por ejemplo `PS3OnlineDNSTester`.
4. Sube todos los archivos y carpetas del proyecto al repositorio.
5. Entra en la pestaña **Actions**.
6. Selecciona **Build APK**.
7. Pulsa **Run workflow** si quieres ejecutarlo manualmente, o simplemente haz un commit/push a `main`/`master`.
8. Espera a que termine en verde.
9. Abre la ejecución terminada y busca **Artifacts**.
10. Descarga `PS3OnlineDNSTester-debug`.
11. Dentro encontrarás `app-debug.apk`.
12. Instálalo en Android permitiendo la instalación desde esa fuente si el teléfono lo solicita.

El workflow usa Java 17 y Gradle 8.9. Android Gradle Plugin 8.7 requiere como mínimo Gradle 8.9.

## Señalización para DOS MÓVILES

La app necesita una URL `wss://...` de señalización. El proyecto incluye un Worker en `worker/` para Cloudflare Workers + Durable Objects.

Pasos generales:

1. Crea tu cuenta de Cloudflare.
2. Instala Wrangler en un PC, o usa el entorno de desarrollo de Cloudflare.
3. Entra en `worker/`.
4. Ejecuta `npx wrangler login`.
5. Ejecuta `npx wrangler deploy`.
6. Copia la URL HTTPS del Worker y conviértela en `wss://...` para colocarla en la app.

El Worker solo intercambia señalización entre los dos teléfonos; el objetivo del test es que el tráfico UDP de prueba vaya directamente entre ellos.

## Fuentes de puertos documentados

- Sony PS3 manual: https://manuals.playstation.net/document/es/ps3/current/settings/connecttest.html
- Activision: https://support.activision.com/es/articles/ports-used-for-call-of-duty-games
- Rockstar GTA IV: https://support.rockstargames.com/articles/6YpjOeQMBquEhnZ6HG3DFh/which-ports-are-required-to-play-gta-iv-via-the-ps3-network

## Limitación importante

Esta versión es una herramienta de diagnóstico. El P2P UDP móvil↔móvil es una prueba real, pero no equivale a ejecutar el protocolo de red del juego PS3. El resultado final que debe interpretarse es `CANDIDATA PARA PROBAR EN PS3`.
