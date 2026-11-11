package co.ealondono.hisenseremote;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Busca televisores en la red local (/24) probando los puertos conocidos:
 *  36669 = Hisense VIDAA (MQTT), 8060 = Roku TV (ECP), 6466 = Android/Google TV (control remoto).
 */
public class NetScanner {

    public static final class Found {
        public final String ip;
        public final String kind;
        Found(String ip, String kind) { this.ip = ip; this.kind = kind; }
        @Override public String toString() { return ip + "  ·  " + kind; }
    }

    /** IPv4 local del celular en la red Wi-Fi (o null). */
    public static String localIpv4() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName();
                if (!(name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("ap"))) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    public static List<Found> scan(String localIp) throws InterruptedException {
        String prefix = localIp.substring(0, localIp.lastIndexOf('.') + 1);
        List<Found> out = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(48);
        for (int i = 1; i < 255; i++) {
            final String ip = prefix + i;
            if (ip.equals(localIp)) continue;
            pool.execute(() -> {
                if (open(ip, VidaaRemote.PORT)) out.add(new Found(ip, "Hisense VIDAA"));
                else if (open(ip, 8060)) out.add(new Found(ip, "Roku TV"));
                else if (open(ip, 6466)) out.add(new Found(ip, "Google/Android TV"));
            });
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        List<Found> sorted = new ArrayList<>(out);
        sorted.sort((a, b) -> Integer.compare(last(a.ip), last(b.ip)));
        return sorted;
    }

    /** Revisa qué puertos de TV responden en una IP y explica qué tipo de equipo es. Bloqueante. */
    public static String diagnose(String ip) {
        if (ip == null || ip.isEmpty()) return "no hay IP.";
        boolean vidaa = open(ip, VidaaRemote.PORT), roku = open(ip, 8060);
        boolean atv = open(ip, 6466) || open(ip, 6467);
        if (vidaa) return "en " + ip + " hay un TV Hisense VIDAA (puerto 36669 abierto). Use el modo «Wi-Fi · Hisense VIDAA». "
                + "Si aun así falla, su firmware exige el certificado de la app oficial de Hisense: use infrarrojo o la app oficial VIDAA.";
        if (roku) return "en " + ip + " hay un Roku TV (puerto 8060). Use el modo «Wi-Fi · Hisense Roku TV».";
        if (atv) return "en " + ip + " hay un Google/Android TV (puertos 6466/6467). Ese tipo de TV no se controla por los modos Wi-Fi de esta app: "
                + "use «Infrarrojo · perfil B» o el control de la app oficial Google TV / Google Home.";
        return "ningún puerto de TV responde en " + ip + ". Puede que esa IP no sea el TV (p. ej. es el router u otro equipo), "
                + "que el TV esté apagado/en reposo, o que el celular esté en otra red (datos móviles o Wi-Fi de invitados). Use «Buscar».";
    }

    private static int last(String ip) { return Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1)); }

    private static boolean open(String ip, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
