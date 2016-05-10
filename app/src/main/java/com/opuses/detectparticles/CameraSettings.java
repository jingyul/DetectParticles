package com.opuses.detectparticles;

/**
 * Created by jingyuli on 3/17/16.
 */
public class CameraSettings {
    private int     _focus;
    private long    _exposure;
    private int     _iso;
    private int     _flash;

    public void CameraSettings(int focus, long exposure, int iso, int flash) {
        _focus = focus;
        _exposure = exposure;
        _iso = iso;
        _flash = flash;
    }

    public void set_focus(int focus) {
        _focus = focus;
    }

    public void set_flash(int flash) {
        _flash = flash;
    }

    public void set_iso(int iso) {
        _iso = iso;
    }

    public void set_exposure(long exposure) {
        _exposure = exposure;
    }

    public int get_focus() {
        return _focus;
    }

    public long get_exposure() {
        return _exposure;
    }

    public int get_iso() {
        return _iso;
    }

    public int get_flash() {
        return _flash;
    }
}
