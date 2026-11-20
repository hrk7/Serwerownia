package serwer.sla;

import serwer.sim.RunResult;

import java.util.List;

public final class SlaEvaluator {

    private SlaEvaluator() {
    }

    public static SlaResult check(SlaLevel l, RunResult r) {
        return new SlaResult(
                r.availability() > l.minAvailability(),
                r.outageCount() < l.maxOutages(),
                r.longestOutagePeriods() < l.maxOutageLenPeriods(),
                r.mtrsH() < l.maxMeanRepairH());          // jednostka: godziny
    }

    public static SlaSummary summarize(SlaLevel l, List<RunResult> runs) {
        int a = 0, o = 0, m = 0, t = 0, all = 0;
        for (RunResult r : runs) {
            SlaResult s = check(l, r);
            if (s.availability()) a++;
            if (s.outages()) o++;
            if (s.longestOutage()) m++;
            if (s.meanRepair()) t++;
            if (s.all()) all++;
        }
        double n = runs.size();
        return new SlaSummary(l, runs.size(), a / n, o / n, m / n, t / n, all / n);
    }

    /** Koszt niedostępności po stronie klienta: KC = U * S * (1 - A), S = uzgodniony czas usługi w h. */
    public static double clientDowntimeCost(double lossPerHour, double agreedServiceH, double availability) {
        return lossPerHour * agreedServiceH * (1.0 - availability);
    }
}
