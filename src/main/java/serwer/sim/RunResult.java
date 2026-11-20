package serwer.sim;

public record RunResult(double horizonH, double[] outageLengthsH) {

    public static final double PERIODS_PER_HOUR = 6.0;     // okres = 10 minut

    /** Długość przerwy w okresach 10-minutowych, zaokrąglona w górę. */
    public static int toPeriods(double hours) {
        return (int) Math.ceil(hours * PERIODS_PER_HOUR - 1e-9);
    }

    public int outageCount() {
        return outageLengthsH.length;
    }

    public double downtimeH() {
        double s = 0;
        for (double d : outageLengthsH) s += d;
        return s;
    }

    public double uptimeH() {
        return horizonH - downtimeH();
    }

    /** Dostępność = udział czasu, w którym phi_sys = 1 (usługa 24/7, więc cały rok). */
    public double availability() {
        return uptimeH() / horizonH;
    }

    public int longestOutagePeriods() {
        int max = 0;
        for (double d : outageLengthsH) max = Math.max(max, toPeriods(d));
        return max;
    }

    /** MTRS = przestój całkowity / N. */
    public double mtrsH() {
        return outageCount() == 0 ? 0 : downtimeH() / outageCount();
    }

    /** Średni czas naprawy w okresach 10-minutowych (do porównania z progiem SLA w kroku 7). */
    public double meanRepairPeriods() {
        return mtrsH() * PERIODS_PER_HOUR;
    }

    /** MTBF = czas sprawności / N (NaN, gdy w roku nie było przerwy). */
    public double mtbfH() {
        return outageCount() == 0 ? Double.NaN : uptimeH() / outageCount();
    }

    /** MTBSI = MTBF + MTRS. */
    public double mtbsiH() {
        return outageCount() == 0 ? Double.NaN : mtbfH() + mtrsH();
    }
}