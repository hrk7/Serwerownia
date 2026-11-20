package serwer;

import serwer.model.StorageType;
import serwer.model.SystemParams;
import serwer.sim.Experiment;
import serwer.sim.RunResult;

import java.util.Arrays;
import java.util.List;

public class Validation {

    /** System, w którym nic się nie psuje (poza tym, co za chwilę włączymy). */
    static SystemParams immortalSystem() {
        SystemParams p = new SystemParams();
        p.upsMttf = 1e12;
        p.switchMttf = 1e12;
        p.serverEta = 1e12;
        p.ctrlMttf = 1e12;
        p.diskEta = 1e12;
        p.reactionTimeH = 0;
        return p;
    }

    static void check(String label, SystemParams p, double theory, int runs, long seed) {
        List<RunResult> results = Experiment.runMany(p, runs, seed);
        double[] a = results.stream().mapToDouble(RunResult::availability).toArray();
        double mean = Arrays.stream(a).average().orElse(Double.NaN);
        double variance = Arrays.stream(a).map(x -> (x - mean) * (x - mean)).sum() / (runs - 1);
        double se = Math.sqrt(variance / runs);          // błąd standardowy średniej
        System.out.println(label);
        System.out.printf("  teoria = %.6f  symulacja = %.6f  różnica = %+.6f  SE = %.6f  |różnica|/SE = %.2f%n%n",
                theory, mean, mean - theory, se, Math.abs(mean - theory) / se);
    }

    static double binomial(int n, int k) {
        double r = 1;
        for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
        return r;
    }

    public static void main(String[] args) {
        // 1) UPS 1 z 2: MTTF = 100 h, MTTR = 4 h
        SystemParams ups = immortalSystem();
        ups.upsMttf = 100;
        ups.upsRepairMean = 4;
        double q = 4.0 / 104;
        check("UPS 1 z 2", ups, 1 - q * q, 1000, 1);

        // 2) kontroler: MTTF = 500 h, MTTR = 8 h (reakcja) + 4 h
        SystemParams ctrl = immortalSystem();
        ctrl.ctrlMttf = 500;
        ctrl.ctrlRepairMean = 4;
        ctrl.reactionTimeH = 8;
        check("Kontroler", ctrl, 500.0 / 512, 1000, 2);

        // 3) dyski RAID 10: MTTF = 200 h (beta = 1), naprawa stała 12 h
        SystemParams raid10 = immortalSystem();
        raid10.diskBeta = 1.0;
        raid10.diskEta = 200;
        raid10.diskRebuildH = 12;
        double qDisk = 12.0 / 212;
        check("Dyski RAID 10", raid10, Math.pow(1 - qDisk * qDisk, 3), 1000, 3);

        // 4) dyski RAID 6: działa, gdy niesprawne są co najwyżej 2 z 6
        SystemParams raid6 = raid10.copy();
        raid6.storage = StorageType.RAID6;
        double theoryRaid6 = 0;
        for (int k = 0; k <= 2; k++) {
            theoryRaid6 += binomial(6, k) * Math.pow(qDisk, k) * Math.pow(1 - qDisk, 6 - k);
        }
        check("Dyski RAID 6", raid6, theoryRaid6, 1000, 4);
    }
}