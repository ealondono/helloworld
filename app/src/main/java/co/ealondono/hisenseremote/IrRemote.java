package co.ealondono.hisenseremote;

import android.hardware.ConsumerIrManager;

import java.util.EnumMap;
import java.util.Map;

/**
 * Control por infrarrojo usando el emisor IR del celular (ConsumerIrManager).
 *
 * Los códigos provienen de Flipper-IRDB (https://github.com/Lucaslhm/Flipper-IRDB, licencia CC0),
 * carpeta TVs/Hisense. Hisense usa dos familias de códigos según el modelo:
 *  - Perfil A: NEC extendido, dirección 0x00 0xBF (VIDAA y la mayoría de modelos: 32A4HAU, 55E7KQ,
 *    58R5, 65K3300UW, K321UW...).
 *  - Perfil B: NEC estándar, dirección 0x04 (modelos Google TV / Android TV recientes: 55U6K, U7G,
 *    55K3201GUWUS).
 */
public class IrRemote implements Remote {

    public enum Profile {
        A_VIDAA("IR perfil A (VIDAA y mayoría)"),
        B_GOOGLE("IR perfil B (Google/Android TV)");

        public final String label;
        Profile(String label) { this.label = label; }
    }

    private static final int CARRIER_HZ = 38000;
    // Tiempos NEC en microsegundos
    private static final int LEAD_ON = 9000, LEAD_OFF = 4500, BIT_ON = 560, ZERO_OFF = 560, ONE_OFF = 1690;

    private final ConsumerIrManager ir;
    private final Profile profile;
    private final Map<Key, Integer> codes = new EnumMap<>(Key.class);

    public IrRemote(ConsumerIrManager ir, Profile profile) {
        this.ir = ir;
        this.profile = profile;
        if (profile == Profile.A_VIDAA) loadProfileA(); else loadProfileB();
    }

    private void loadProfileA() {
        put(Key.POWER, 0x0D); put(Key.INPUT, 0x12); put(Key.HOME, 0x20); put(Key.MENU, 0x14);
        put(Key.BACK, 0x48); put(Key.EXIT, 0x5C);
        put(Key.UP, 0x16); put(Key.DOWN, 0x17); put(Key.LEFT, 0x19); put(Key.RIGHT, 0x18); put(Key.OK, 0x15);
        put(Key.VOL_UP, 0x44); put(Key.VOL_DOWN, 0x43); put(Key.MUTE, 0x0E);
        put(Key.CH_UP, 0x4A); put(Key.CH_DOWN, 0x4B);
        put(Key.PLAY, 0xA8); put(Key.PAUSE, 0xA9); put(Key.STOP, 0x5A); put(Key.REWIND, 0x58); put(Key.FORWARD, 0x59);
        Key[] d = digits();
        for (int i = 0; i <= 9; i++) put(d[i], i); // 0x00..0x09
    }

    private void loadProfileB() {
        put(Key.POWER, 0x08); put(Key.INPUT, 0x0B); put(Key.HOME, 0x4A); put(Key.MENU, 0x43);
        put(Key.BACK, 0x04); put(Key.EXIT, 0x4D);
        put(Key.UP, 0x56); put(Key.DOWN, 0x57); put(Key.LEFT, 0x58); put(Key.RIGHT, 0x59); put(Key.OK, 0x5A);
        put(Key.VOL_UP, 0x02); put(Key.VOL_DOWN, 0x03); put(Key.MUTE, 0x09);
        put(Key.CH_UP, 0x00); put(Key.CH_DOWN, 0x01);
        put(Key.PLAY, 0x42); put(Key.PAUSE, 0x41); put(Key.STOP, 0x4E); put(Key.REWIND, 0x4F); put(Key.FORWARD, 0x5B);
        Key[] d = digits();
        put(d[0], 0x10);
        for (int i = 1; i <= 9; i++) put(d[i], 0x10 + i); // 0x11..0x19
    }

    static Key[] digits() {
        return new Key[]{Key.D0, Key.D1, Key.D2, Key.D3, Key.D4, Key.D5, Key.D6, Key.D7, Key.D8, Key.D9};
    }

    private void put(Key k, int cmd) { codes.put(k, cmd); }

    @Override public boolean supports(Key key) { return codes.containsKey(key); }

    @Override
    public void send(Key key) throws Exception {
        Integer cmd = codes.get(key);
        if (cmd == null) throw new IllegalArgumentException("Tecla no disponible por IR");
        byte[] bytes = (profile == Profile.A_VIDAA)
                ? new byte[]{0x00, (byte) 0xBF, (byte) (int) cmd, (byte) ~cmd}      // NEC extendido
                : new byte[]{0x04, (byte) ~0x04, (byte) (int) cmd, (byte) ~cmd};    // NEC estándar
        ir.transmit(CARRIER_HZ, necPattern(bytes));
    }

    /** Construye la trama NEC: líder, 32 bits (LSB primero por byte) y bit de parada. */
    static int[] necPattern(byte[] bytes) {
        int[] p = new int[2 + bytes.length * 16 + 1];
        int i = 0;
        p[i++] = LEAD_ON;
        p[i++] = LEAD_OFF;
        for (byte b : bytes) {
            for (int bit = 0; bit < 8; bit++) {
                p[i++] = BIT_ON;
                p[i++] = ((b >> bit) & 1) == 1 ? ONE_OFF : ZERO_OFF;
            }
        }
        p[i] = BIT_ON;
        return p;
    }
}
