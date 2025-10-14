
package com.civlabs.radios.core;

import com.civlabs.radios.model.Radio;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FrequencyManager {
    private int maxFrequencies;
    private final Map<Integer, UUID> active = new HashMap<>();

    public FrequencyManager(int maxFrequencies) {
        this.maxFrequencies = maxFrequencies;
    }

    public int getMaxFrequencies() {
        return maxFrequencies;
    }
    public void setMaxFrecuency( int maxFrequencies){
        this.maxFrequencies = maxFrequencies;
    }

    public void clearFrecuency(){
        active.clear();
    }
    // tries to claim the frequency for the radio passed, return true if successfully claimed the frequency
    public synchronized boolean claim(int f, UUID radioId) {
        if (f < 1 || f > maxFrequencies) return false;
        if(isInUse(f, radioId)) return false;

        active.put(f, radioId);
        return true;
    }

    public synchronized void release(int f, UUID radioId) {
        UUID cur = active.get(f);
        if (cur != null && cur.equals(radioId)) active.remove(f);
    }

    public synchronized void clearAndLoad(Collection<Radio> radios) {
        active.clear();
        for (Radio r : radios) {
            int tx = r.getTransmitFrequency();
            if (r.isEnabled() && tx >= 1 && tx <= maxFrequencies) {
                active.put(tx, r.getId());
            }
        }
    }
    // checks if the frequency f is in use by another radio that's not the one passed in
    public synchronized boolean isInUse(int f, UUID requesterRadioId) {
        UUID holder = active.get(f);
        return holder != null && !holder.equals(requesterRadioId);
    }
}
