package co.ealondono.hisenseremote;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

/**
 * Hisense Roku TV: protocolo oficial External Control Protocol (ECP) de Roku,
 * HTTP POST a http://IP:8060/keypress/{tecla}.
 * Documentación: https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md
 * En el TV debe estar habilitado: Configuración > Sistema > Configuración avanzada >
 * Control por aplicaciones móviles > Acceso de red = Predeterminado/Permisivo.
 */
public class RokuRemote implements Remote {
    private final String host;
    private final Map<Key, String> keys = new EnumMap<>(Key.class);

    public RokuRemote(String host) {
        this.host = host;
        keys.put(Key.POWER, "Power"); keys.put(Key.INPUT, "InputHDMI1"); keys.put(Key.HOME, "Home");
        keys.put(Key.MENU, "Info"); keys.put(Key.BACK, "Back"); keys.put(Key.EXIT, "Home");
        keys.put(Key.UP, "Up"); keys.put(Key.DOWN, "Down"); keys.put(Key.LEFT, "Left"); keys.put(Key.RIGHT, "Right");
        keys.put(Key.OK, "Select");
        keys.put(Key.VOL_UP, "VolumeUp"); keys.put(Key.VOL_DOWN, "VolumeDown"); keys.put(Key.MUTE, "VolumeMute");
        keys.put(Key.CH_UP, "ChannelUp"); keys.put(Key.CH_DOWN, "ChannelDown");
        keys.put(Key.PLAY, "Play"); keys.put(Key.PAUSE, "Play"); keys.put(Key.REWIND, "Rev"); keys.put(Key.FORWARD, "Fwd");
        Key[] d = IrRemote.digits();
        for (int i = 0; i <= 9; i++) keys.put(d[i], "Lit_" + i);
    }

    @Override public boolean supports(Key key) { return keys.containsKey(key); }

    @Override
    public void send(Key key) throws Exception {
        String k = keys.get(key);
        if (k == null) throw new IllegalArgumentException("Tecla no disponible en Roku");
        HttpURLConnection c = (HttpURLConnection) new URL("http://" + host + ":8060/keypress/" + k).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(2500);
            c.setReadTimeout(2500);
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(0);
            c.getOutputStream().close();
            int code = c.getResponseCode();
            if (code == 403) throw new IllegalStateException(
                    "El TV rechazó el control (403). Active 'Control por aplicaciones móviles' en el TV.");
            if (code >= 300) throw new IllegalStateException("Respuesta HTTP " + code);
        } finally {
            c.disconnect();
        }
    }
}
