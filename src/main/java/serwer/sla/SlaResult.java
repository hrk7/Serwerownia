package serwer.sla;

/** Które warunki SLA spełniła jedna realizacja. */
public record SlaResult(boolean availability, boolean outages, boolean longestOutage, boolean meanRepair) {

    public boolean all() {
        return availability && outages && longestOutage && meanRepair;
    }
}