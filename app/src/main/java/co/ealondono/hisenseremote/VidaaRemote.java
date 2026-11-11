package co.ealondono.hisenseremote;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Hisense VIDAA por Wi-Fi: el TV expone un broker MQTT en el puerto 36669.
 * Temas, credenciales y nombres de tecla según la librería abierta hisensetv
 * (https://github.com/newAM/hisensetv, licencia MIT).
 *
 * Limitación conocida: algunos firmwares VIDAA recientes exigen un certificado de cliente
 * propietario de la app oficial; esta app NO lo incluye (no es redistribuible). En esos
 * modelos la conexión será rechazada y se debe usar IR o la app oficial "VIDAA".
 */
public class VidaaRemote implements Remote {
    public static final int PORT = 36669;
    private static final String USER = "hisenseservice";
    private static final String PASS = "multimqttservice";
    private static final String DEVICE = "XX:XX:XX:XX:XX:XY$normal";

    private final String host;
    private MqttClient client;
    private final Map<Key, String> keys = new EnumMap<>(Key.class);

    public VidaaRemote(String host) {
        this.host = host;
        keys.put(Key.POWER, "KEY_POWER"); keys.put(Key.HOME, "KEY_HOME"); keys.put(Key.MENU, "KEY_MENU");
        keys.put(Key.BACK, "KEY_RETURNS"); keys.put(Key.EXIT, "KEY_EXIT");
        keys.put(Key.UP, "KEY_UP"); keys.put(Key.DOWN, "KEY_DOWN"); keys.put(Key.LEFT, "KEY_LEFT");
        keys.put(Key.RIGHT, "KEY_RIGHT"); keys.put(Key.OK, "KEY_OK");
        keys.put(Key.VOL_UP, "KEY_VOLUMEUP"); keys.put(Key.VOL_DOWN, "KEY_VOLUMEDOWN"); keys.put(Key.MUTE, "KEY_MUTE");
        keys.put(Key.CH_UP, "KEY_CHANNELUP"); keys.put(Key.CH_DOWN, "KEY_CHANNELDOWN");
        keys.put(Key.PLAY, "KEY_PLAY"); keys.put(Key.PAUSE, "KEY_PAUSE"); keys.put(Key.STOP, "KEY_STOP");
        keys.put(Key.REWIND, "KEY_BACK"); keys.put(Key.FORWARD, "KEY_FORWARDS");
        Key[] d = IrRemote.digits();
        for (int i = 0; i <= 9; i++) keys.put(d[i], "KEY_" + i);
        // INPUT no se declara: la librería de referencia no documenta una tecla de fuente para VIDAA,
        // por eso ese botón queda deshabilitado en este modo.
    }

    @Override public boolean supports(Key key) { return keys.containsKey(key); }

    private synchronized MqttClient ensureConnected() throws Exception {
        if (client != null && client.isConnected()) return client;
        Exception last = null;
        // Primero TLS (la mayoría de modelos), luego TCP plano (algunos modelos antiguos).
        for (String scheme : new String[]{"ssl", "tcp"}) {
            try {
                MqttClient c = new MqttClient(scheme + "://" + host + ":" + PORT,
                        "HisenseRemote/" + UUID.randomUUID(), new MemoryPersistence());
                MqttConnectOptions o = new MqttConnectOptions();
                o.setUserName(USER);
                o.setPassword(PASS.toCharArray());
                o.setConnectionTimeout(4);
                o.setKeepAliveInterval(30);
                o.setCleanSession(true);
                o.setAutomaticReconnect(false);
                o.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
                if (scheme.equals("ssl")) {
                    o.setSocketFactory(trustAllContext().getSocketFactory());
                    o.setHttpsHostnameVerificationEnabled(false);
                }
                c.connect(o);
                client = c;
                // Pide el estado: en modelos que requieren emparejamiento, esto muestra un PIN en el TV.
                publish("ui_service", "gettvstate", "");
                return c;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IllegalStateException(describe(last), last);
    }

    private static String describe(Exception e) {
        if (e instanceof MqttException) {
            int code = ((MqttException) e).getReasonCode();
            if (code == MqttException.REASON_CODE_FAILED_AUTHENTICATION
                    || code == MqttException.REASON_CODE_NOT_AUTHORIZED) {
                return "El TV rechazó la autenticación. Este firmware VIDAA exige el certificado de la app oficial: use IR o la app VIDAA.";
            }
        }
        Throwable root = e;
        while (root != null && root.getCause() != null && root.getCause() != root) root = root.getCause();
        String detail = root == null ? "" : " [" + root.getClass().getSimpleName()
                + (root.getMessage() != null ? ": " + root.getMessage() : "") + "]";
        if (root instanceof javax.net.ssl.SSLException) {
            return "El TV VIDAA rechazó la conexión segura: este firmware exige el certificado de la app oficial de Hisense." + detail;
        }
        return "No se pudo conectar al TV VIDAA (puerto 36669)." + detail;
    }

    private void publish(String service, String action, String payload) throws MqttException {
        String topic = "/remoteapp/tv/" + service + "/" + DEVICE + "/actions/" + action;
        MqttMessage m = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        m.setQos(1);
        client.publish(topic, m);
    }

    @Override
    public void send(Key key) throws Exception {
        String k = keys.get(key);
        if (k == null) throw new IllegalArgumentException("Tecla no disponible en VIDAA");
        ensureConnected();
        try {
            publish("remote_service", "sendkey", k);
        } catch (MqttException e) {
            close();
            ensureConnected();
            publish("remote_service", "sendkey", k);
        }
    }

    /** Envía el PIN que el TV muestra en pantalla (modelos que piden emparejamiento). */
    public void sendPin(String pin) throws Exception {
        ensureConnected();
        publish("ui_service", "authenticationcode", "{\"authNum\":\"" + pin.replaceAll("[^0-9]", "") + "\"}");
    }

    @Override
    public synchronized void close() {
        if (client != null) {
            try { if (client.isConnected()) client.disconnect(500); } catch (Exception ignored) { }
            try { client.close(); } catch (Exception ignored) { }
            client = null;
        }
    }

    /**
     * El TV usa un certificado autofirmado. Solo se acepta para esta conexión local al TV
     * (red doméstica); no se usa para ninguna otra comunicación.
     */
    private static SSLContext trustAllContext() throws Exception {
        TrustManager[] tm = {new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] c, String a) { }
            @Override public void checkServerTrusted(X509Certificate[] c, String a) { }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tm, new SecureRandom());
        return ctx;
    }
}
