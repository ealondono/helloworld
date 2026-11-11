# Control Hisense (Android)

App de control remoto para televisores Hisense, **sin anuncios, sin cuentas y sin conexión a Internet**:
todo ocurre entre el celular y el TV.

## Descargar e instalar

1. Desde el celular abra: <https://github.com/ealondono/helloworld/releases/tag/latest>
2. Descargue `ControlHisense.apk` y ábralo.
3. Android pedirá permitir "Instalar apps desconocidas" para el navegador: acéptelo solo para esta instalación.

## Modos de control

| Modo | Requisito | Enciende el TV apagado |
|---|---|---|
| Infrarrojo · perfil A | Celular con emisor IR (p. ej. muchos Xiaomi/Redmi/Poco). TV VIDAA y la mayoría de modelos Hisense | Sí |
| Infrarrojo · perfil B | Celular con emisor IR. TV Hisense con Google TV / Android TV | Sí |
| Wi‑Fi · VIDAA | TV y celular en la misma red Wi‑Fi | Normalmente no* |
| Wi‑Fi · Roku TV | Misma red Wi‑Fi y "Control por aplicaciones móviles" habilitado en el TV | Según configuración |

\* Por Wi‑Fi el TV apagado suele desconectar la red, salvo que tenga activado el encendido por red.

- **Buscar**: explora la red local y detecta TVs VIDAA (puerto 36669), Roku (8060) y Google/Android TV (6466).
- **PIN**: algunos TV VIDAA muestran un código de emparejamiento; escríbalo ahí.
- Flechas, volumen y canal se repiten si se mantienen presionados.

## Limitaciones conocidas (honestas)

- **Infrarrojo** solo funciona si el celular tiene emisor IR. La app avisa si no lo tiene.
- **VIDAA por Wi‑Fi**: algunos firmwares recientes exigen un certificado de cliente propietario de la app
  oficial de Hisense; esta app no lo incluye porque no es redistribuible. En esos casos use IR o la app oficial
  **VIDAA** (gratuita, de Hisense).
- **Google TV / Android TV por Wi‑Fi** no está implementado: use el perfil IR B, o el control integrado de la
  app oficial **Google TV / Google Home**, que es gratuito y sin anuncios.

## Fuentes de los códigos y protocolos

- Códigos IR Hisense: [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB) (`TVs/Hisense`, licencia CC0).
- Protocolo VIDAA (MQTT, puerto 36669): librería abierta [hisensetv](https://github.com/newAM/hisensetv) (MIT).
- Roku: [External Control Protocol](https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md) (documentación oficial de Roku).

## Compilar

Requiere JDK 17 y Android SDK (compileSdk 35):

```bash
./gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/`. GitHub Actions lo compila y publica automáticamente
(`.github/workflows/android.yml`).
