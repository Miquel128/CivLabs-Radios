package com.civlabs.radios.voice;

import java.util.concurrent.ThreadLocalRandom;

/** Very lightweight band-limited noise (telephone band) for radio static. */
public class Interference {

    // One-pole HP/LP to roughly confine noise to 300–3400 Hz
    private static final class OnePole {
        double a0, a1, b1, z1;
        void setHP(double fc, double fs) { // 1st-order high-pass
            double x = Math.exp(-2.0 * Math.PI * fc / fs);
            a0 = (1 + x) / 2.0; a1 = -(1 + x) / 2.0; b1 = x;
        }
        void setLP(double fc, double fs) { // 1st-order low-pass
            double x = Math.exp(-2.0 * Math.PI * fc / fs);
            a0 = 1 - x; a1 = 0; b1 = x;
        }
        float process(float x) {
            double y = a0 * x + a1 * z1 + b1 * z1;
            z1 = y;
            return (float) y;
        }
    }

    private final OnePole hp = new OnePole();
    private final OnePole lp = new OnePole();
    private final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    private final int sampleRate;

    public Interference(int sampleRate) {
        this.sampleRate = sampleRate;
        hp.setHP(300.0, sampleRate);
        lp.setLP(3400.0, sampleRate);
    }

    /** Mixes radio-static into pcm in-place at the given linear amplitude (0..~0.4). */
    public void mixStatic(short[] pcm, double noiseAmp) {
        if (noiseAmp <= 0) return;
        for (int i = 0; i < pcm.length; i++) {
            // white noise → band-limit
            float n = (rnd.nextFloat() * 2f - 1f);
            n = hp.process(n);
            n = lp.process(n);

            // (optional) flutter wobble removed for stability
            double wobble = 1.0;

            double x = pcm[i] / 32768.0;
            x += noiseAmp * wobble * n;

            if (x > 1.0) x = 1.0;
            if (x < -1.0) x = -1.0;
            pcm[i] = (short) Math.round(x * 32767.0);
        }
    }

    /** Convert dBFS (negative) to linear amplitude. */
    public static double dbToLin(double db) {
        return Math.pow(10.0, db / 20.0);
    }
}
