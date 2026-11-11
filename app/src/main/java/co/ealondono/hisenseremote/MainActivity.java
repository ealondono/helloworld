package co.ealondono.hisenseremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pantalla única del control remoto. Interfaz construida en código: sin anuncios, sin rastreo, sin cuentas. */
public class MainActivity extends Activity {

    private enum Mode {
        IR_A("Infrarrojo · perfil A (VIDAA / mayoría)"),
        IR_B("Infrarrojo · perfil B (Google/Android TV)"),
        VIDAA("Wi-Fi · Hisense VIDAA"),
        ROKU("Wi-Fi · Hisense Roku TV");
        final String label;
        Mode(String l) { label = l; }
        boolean isNetwork() { return this == VIDAA || this == ROKU; }
    }

    private static final int BG = Color.rgb(18, 22, 28);
    private static final int BTN = Color.rgb(44, 52, 64);
    private static final int ACCENT = Color.rgb(0, 166, 166);
    private static final int RED = Color.rgb(200, 48, 48);
    private static final int TEXT = Color.rgb(236, 240, 244);
    private static final int MUTED = Color.rgb(150, 160, 172);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<Key, Button> buttons = new EnumMap<>(Key.class);

    private SharedPreferences prefs;
    private ConsumerIrManager irManager;
    private volatile Remote remote;
    private final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger();
    private volatile long lastDiagnosis;
    private volatile Mode mode;

    private Spinner modeSpinner;
    private LinearLayout netRow;
    private EditText ipField;
    private Button pinButton;
    private TextView status;
    private LinearLayout numPad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("remote", MODE_PRIVATE);
        irManager = (ConsumerIrManager) getSystemService(Context.CONSUMER_IR_SERVICE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());

        Mode saved = Mode.values()[Math.min(prefs.getInt("mode", defaultMode().ordinal()), Mode.values().length - 1)];
        modeSpinner.setSelection(saved.ordinal());
        applyMode(saved);
    }

    private boolean hasIr() {
        return irManager != null && irManager.hasIrEmitter();
    }

    private Mode defaultMode() { return hasIr() ? Mode.IR_A : Mode.VIDAA; }

    // ------------------------------------------------------------------ UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(16);
        root.setPadding(pad, dp(12), pad, pad);

        TextView title = text("Control Hisense", 22, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        // Selector de modo
        Spinner spinner = new Spinner(this);
        modeSpinner = spinner;
        List<String> labels = new ArrayList<>();
        for (Mode m : Mode.values()) labels.add(m.label);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int p, View c, ViewGroup g) {
                TextView v = (TextView) super.getView(p, c, g); v.setTextColor(TEXT); return v;
            }
        };
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(ad);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                Mode m = Mode.values()[pos];
                if (m != mode) applyMode(m);
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        root.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));

        // Fila de red: IP + buscar + PIN
        netRow = new LinearLayout(this);
        netRow.setOrientation(LinearLayout.HORIZONTAL);
        netRow.setGravity(Gravity.CENTER_VERTICAL);
        ipField = new EditText(this);
        ipField.setHint("IP del TV, ej. 192.168.1.50");
        ipField.setHintTextColor(MUTED);
        ipField.setTextColor(TEXT);
        ipField.setSingleLine(true);
        ipField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        ipField.setText(prefs.getString("ip", ""));
        ipField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { if (mode != null) applyMode(mode); }
        });
        netRow.addView(ipField, new LinearLayout.LayoutParams(0, -2, 1f));
        Button scan = smallButton("Buscar");
        scan.setOnClickListener(v -> scanNetwork());
        netRow.addView(scan);
        pinButton = smallButton("PIN");
        pinButton.setOnClickListener(v -> askPin());
        netRow.addView(pinButton);
        root.addView(netRow);

        status = text("", 13, MUTED);
        status.setPadding(0, dp(4), 0, dp(8));
        root.addView(status);

        // Contenido desplazable con los botones
        ScrollView scroll = new ScrollView(this);
        LinearLayout pad2 = new LinearLayout(this);
        pad2.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(pad2);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        pad2.addView(row(key(Key.POWER, "Encender", RED), key(Key.INPUT, "Fuente", BTN), key(Key.MUTE, "Silencio", BTN)));
        pad2.addView(spacer(10));

        // Cruceta
        pad2.addView(row(blank(), key(Key.UP, "▲", BTN), blank()));
        pad2.addView(row(key(Key.LEFT, "◀", BTN), key(Key.OK, "OK", ACCENT), key(Key.RIGHT, "▶", BTN)));
        pad2.addView(row(blank(), key(Key.DOWN, "▼", BTN), blank()));
        pad2.addView(spacer(10));

        pad2.addView(row(key(Key.BACK, "↩ Atrás", BTN), key(Key.HOME, "⌂ Inicio", BTN), key(Key.MENU, "☰ Menú", BTN)));
        pad2.addView(spacer(10));

        // Volumen y canales
        pad2.addView(row(key(Key.VOL_UP, "Vol +", BTN), key(Key.EXIT, "Salir", BTN), key(Key.CH_UP, "Can +", BTN)));
        pad2.addView(row(key(Key.VOL_DOWN, "Vol −", BTN), blank(), key(Key.CH_DOWN, "Can −", BTN)));
        pad2.addView(spacer(10));

        // Multimedia
        LinearLayout media = row(key(Key.REWIND, "◀◀", BTN), key(Key.PLAY, "▶", BTN), key(Key.PAUSE, "❚❚", BTN));
        pad2.addView(media);
        pad2.addView(row(key(Key.STOP, "■", BTN), blank(), key(Key.FORWARD, "▶▶", BTN)));
        pad2.addView(spacer(10));

        // Teclado numérico plegable
        Button toggle = smallButton("Mostrar números");
        toggle.setOnClickListener(v -> {
            boolean show = numPad.getVisibility() != View.VISIBLE;
            numPad.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? "Ocultar números" : "Mostrar números");
        });
        pad2.addView(toggle, new LinearLayout.LayoutParams(-1, dp(44)));
        numPad = new LinearLayout(this);
        numPad.setOrientation(LinearLayout.VERTICAL);
        numPad.setVisibility(View.GONE);
        Key[] d = IrRemote.digits();
        numPad.addView(row(key(d[1], "1", BTN), key(d[2], "2", BTN), key(d[3], "3", BTN)));
        numPad.addView(row(key(d[4], "4", BTN), key(d[5], "5", BTN), key(d[6], "6", BTN)));
        numPad.addView(row(key(d[7], "7", BTN), key(d[8], "8", BTN), key(d[9], "9", BTN)));
        numPad.addView(row(blank(), key(d[0], "0", BTN), blank()));
        pad2.addView(numPad);

        TextView foot = text("Sin anuncios · Sin conexión a Internet · Todo ocurre entre su celular y el TV.", 11, MUTED);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(14), 0, 0);
        pad2.addView(foot);
        return root;
    }

    private LinearLayout row(View... views) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : views) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1f);
            lp.setMargins(dp(5), dp(5), dp(5), dp(5));
            r.addView(v, lp);
        }
        return r;
    }

    private View blank() { return new View(this); }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable shape(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        return g;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setBackground(shape(BTN));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.setMargins(dp(6), 0, 0, 0);
        b.setLayoutParams(lp);
        b.setPadding(dp(14), 0, dp(14), 0);
        return b;
    }

    private Button key(Key k, String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(TEXT);
        b.setBackground(shape(color));
        b.setPadding(0, 0, 0, 0);
        attachPress(b, k);
        buttons.put(k, b);
        return b;
    }

    /** Toque = envía una vez; mantener presionado en flechas/volumen/canal = repite. */
    private void attachPress(Button b, Key k) {
        final Runnable[] repeater = new Runnable[1];
        b.setOnTouchListener((v, ev) -> {
            if (!v.isEnabled()) return false;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    v.setAlpha(0.6f);
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    send(k);
                    if (k.repeats()) {
                        repeater[0] = new Runnable() {
                            @Override public void run() { send(k); ui.postDelayed(this, 160); }
                        };
                        ui.postDelayed(repeater[0], 450);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    v.setAlpha(1f);
                    if (repeater[0] != null) { ui.removeCallbacks(repeater[0]); repeater[0] = null; }
                    if (ev.getActionMasked() == MotionEvent.ACTION_UP) v.performClick();
                    return true;
            }
            return false;
        });
    }

    // ------------------------------------------------------------------ Lógica

    private void applyMode(Mode m) {
        mode = m;
        lastDiagnosis = 0;
        prefs.edit().putInt("mode", m.ordinal()).apply();
        Remote old = remote;
        remote = null;
        if (old != null) io.execute(old::close);

        netRow.setVisibility(m.isNetwork() ? View.VISIBLE : View.GONE);
        pinButton.setVisibility(m == Mode.VIDAA ? View.VISIBLE : View.GONE);

        String ip = ipField.getText().toString().trim();
        prefs.edit().putString("ip", ip).apply();

        switch (m) {
            case IR_A:
            case IR_B:
                if (!hasIr()) {
                    setStatus("⚠ Este celular no tiene emisor infrarrojo. Use un modo Wi-Fi.", true);
                } else {
                    remote = new IrRemote(irManager, m == Mode.IR_A ? IrRemote.Profile.A_VIDAA : IrRemote.Profile.B_GOOGLE);
                    setStatus("Apunte el celular al TV. Si no responde, pruebe el otro perfil IR.", false);
                }
                break;
            case VIDAA:
                if (ip.isEmpty()) setStatus("Escriba la IP del TV o toque «Buscar». El TV y el celular deben estar en el mismo Wi-Fi.", false);
                else { remote = new VidaaRemote(ip); setStatus("Listo para " + ip + " (VIDAA). La primera tecla abre la conexión.", false); }
                break;
            case ROKU:
                if (ip.isEmpty()) setStatus("Escriba la IP del TV o toque «Buscar».", false);
                else { remote = new RokuRemote(ip); setStatus("Listo para " + ip + " (Roku).", false); }
                break;
        }
        for (Map.Entry<Key, Button> e : buttons.entrySet()) {
            boolean ok = remote == null || remote.supports(e.getKey());
            e.getValue().setEnabled(ok);
            e.getValue().setAlpha(ok ? 1f : 0.3f);
        }
    }

    private void send(Key k) {
        Remote r = remote;
        if (r == null) {
            setStatus(mode.isNetwork() ? "Primero indique la IP del TV." : "No hay emisor infrarrojo en este celular.", true);
            return;
        }
        // Evita acumular envíos cuando el TV no responde (p. ej. al mantener presionada una flecha).
        if (r != irRemoteOrNull() && pending.get() >= 2) return;
        pending.incrementAndGet();
        io.execute(() -> {
            try {
                if (r != remote) return; // el modo cambió: se descarta el envío pendiente
                r.send(k);
                if (r instanceof VidaaRemote || r instanceof RokuRemote) ui.post(() -> { if (r == remote) setStatus("✓ Conectado", false); });
            } catch (Exception e) {
                if (r != remote) return;
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                String extra = "";
                long now = System.currentTimeMillis();
                if (mode.isNetwork() && now - lastDiagnosis > 8000) {
                    lastDiagnosis = now;
                    extra = "\n\nDiagnóstico: " + NetScanner.diagnose(ipField.getText().toString().trim());
                }
                String full = "✗ " + msg + extra;
                ui.post(() -> { if (r == remote) setStatus(full, true); });
            } finally {
                pending.decrementAndGet();
            }
        });
    }

    private Remote irRemoteOrNull() { return remote instanceof IrRemote ? remote : null; }

    private void setStatus(String s, boolean error) {
        status.setText(s);
        status.setTextColor(error ? Color.rgb(255, 120, 120) : MUTED);
    }

    private void scanNetwork() {
        String local = NetScanner.localIpv4();
        if (local == null) { setStatus("Conéctese al Wi-Fi donde está el TV.", true); return; }
        setStatus("Buscando televisores en la red " + local.substring(0, local.lastIndexOf('.')) + ".x …", false);
        new Thread(() -> {
            List<NetScanner.Found> found;
            try { found = NetScanner.scan(local); } catch (InterruptedException e) { return; }
            ui.post(() -> showScanResult(found));
        }).start();
    }

    private void showScanResult(List<NetScanner.Found> found) {
        if (isFinishing()) return;
        if (found.isEmpty()) {
            setStatus("No se encontró ningún TV. Verifique que esté encendido y en el mismo Wi-Fi.", true);
            return;
        }
        setStatus("Encontrados: " + found.size(), false);
        String[] items = new String[found.size()];
        for (int i = 0; i < items.length; i++) items[i] = found.get(i).toString();
        new AlertDialog.Builder(this)
                .setTitle("Seleccione su TV")
                .setItems(items, (d, which) -> {
                    NetScanner.Found f = found.get(which);
                    ipField.setText(f.ip);
                    if (f.kind.startsWith("Roku")) { modeSpinner.setSelection(Mode.ROKU.ordinal()); applyMode(Mode.ROKU); }
                    else if (f.kind.startsWith("Hisense")) { modeSpinner.setSelection(Mode.VIDAA.ordinal()); applyMode(Mode.VIDAA); }
                    else {
                        new AlertDialog.Builder(this)
                                .setTitle("Google/Android TV")
                                .setMessage("Su TV usa Google TV. Puede controlarlo con:\n\n"
                                        + "• Esta app en modo «Infrarrojo · perfil B», si su celular tiene emisor IR.\n"
                                        + "• El control remoto integrado y gratuito de la app oficial Google TV o Google Home (sin anuncios).")
                                .setPositiveButton("Entendido", null).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void askPin() {
        if (!(remote instanceof VidaaRemote)) { setStatus("Primero indique la IP del TV.", true); return; }
        VidaaRemote v = (VidaaRemote) remote;
        EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setHint("Código que muestra el TV");
        new AlertDialog.Builder(this)
                .setTitle("Emparejar con el TV")
                .setMessage("Si el TV muestra un código de 4 dígitos, escríbalo aquí.")
                .setView(in)
                .setPositiveButton("Enviar", (d, w) -> io.execute(() -> {
                    try { v.sendPin(in.getText().toString()); ui.post(() -> setStatus("PIN enviado.", false)); }
                    catch (Exception e) { ui.post(() -> setStatus("✗ " + e.getMessage(), true)); }
                }))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.edit().putString("ip", ipField.getText().toString().trim()).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Remote r = remote;
        if (r != null) io.execute(r::close);
        io.shutdown();
    }
}
