package co.ealondono.hisenseremote;

/** Teclas lógicas del control. Cada transporte (IR, VIDAA, Roku) las traduce a su propio código. */
public enum Key {
    POWER, INPUT, HOME, MENU, BACK, EXIT,
    UP, DOWN, LEFT, RIGHT, OK,
    VOL_UP, VOL_DOWN, MUTE, CH_UP, CH_DOWN,
    PLAY, PAUSE, STOP, REWIND, FORWARD,
    D0, D1, D2, D3, D4, D5, D6, D7, D8, D9;

    /** Teclas que se repiten mientras se mantienen presionadas. */
    public boolean repeats() {
        switch (this) {
            case UP: case DOWN: case LEFT: case RIGHT:
            case VOL_UP: case VOL_DOWN: case CH_UP: case CH_DOWN:
                return true;
            default:
                return false;
        }
    }
}
