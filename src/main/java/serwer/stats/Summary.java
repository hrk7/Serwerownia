package serwer.stats;

import serwer.sim.RunResult;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record Summary(List<RunResult> runs) {

    public int size() {
        return runs.size();
    }

    public double meanAvailability() {
        return runs.stream().mapToDouble(RunResult::availability).average().orElse(Double.NaN);
    }

    public double minAvailability() {
        return runs.stream().mapToDouble(RunResult::availability).min().orElse(Double.NaN);
    }

    public double meanOutages() {
        return runs.stream().mapToInt(RunResult::outageCount).average().orElse(Double.NaN);
    }

    public double meanLongestOutagePeriods() {
        return runs.stream().mapToInt(RunResult::longestOutagePeriods).average().orElse(Double.NaN);
    }

    public int maxLongestOutagePeriods() {
        return runs.stream().mapToInt(RunResult::longestOutagePeriods).max().orElse(0);
    }

    private double totalDowntimeH() {
        return runs.stream().mapToDouble(RunResult::downtimeH).sum();
    }

    private double totalUptimeH() {
        return runs.stream().mapToDouble(RunResult::uptimeH).sum();
    }

    private int totalOutages() {
        return runs.stream().mapToInt(RunResult::outageCount).sum();
    }

    /** MTRS łączny: cały przestój / wszystkie przerwy ze wszystkich realizacji. */
    public double mtrsH() {
        int n = totalOutages();
        return n == 0 ? Double.NaN : totalDowntimeH() / n;
    }

    public double mtbfH() {
        int n = totalOutages();
        return n == 0 ? Double.NaN : totalUptimeH() / n;
    }

    public double mtbsiH() {
        return mtbfH() + mtrsH();
    }

    public double[] availabilities() {
        return runs.stream().mapToDouble(RunResult::availability).toArray();
    }

    /** Histogram liczby przerw: liczba przerw -> w ilu realizacjach wystąpiła. */
    public Map<Integer, Integer> outageCountHistogram() {
        Map<Integer, Integer> h = new TreeMap<>();
        for (RunResult r : runs) h.merge(r.outageCount(), 1, Integer::sum);
        return h;
    }

    /** Histogram o równych przedziałach; zwraca liczności przedziałów. */
    public static int[] histogram(double[] values, double lo, double hi, int bins) {
        int[] counts = new int[bins];
        double width = (hi - lo) / bins;
        for (double v : values) {
            int b = width == 0 ? 0 : (int) ((v - lo) / width);
            counts[Math.min(Math.max(b, 0), bins - 1)]++;
        }
        return counts;
    }
}
