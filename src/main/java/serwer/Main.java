package serwer;

import serwer.model.SystemParams;
import serwer.sim.Experiment;
import serwer.stats.Summary;

public class Main {

    static void print(String label, Summary s) {
        System.out.println(label);
        System.out.printf("  A: średnia = %.5f, minimum = %.5f%n", s.meanAvailability(), s.minAvailability());
        System.out.printf("  N: średnie = %.2f%n", s.meanOutages());
        System.out.printf("  najdłuższa przerwa: średnia = %.1f, maksimum = %d okresów 10 min%n",
                s.meanLongestOutagePeriods(), s.maxLongestOutagePeriods());
        System.out.printf("  MTRS = %.2f h, MTBF = %.1f h, MTBSI = %.1f h%n%n",
                s.mtrsH(), s.mtbfH(), s.mtbsiH());
    }

    public static void main(String[] args) {
        // 1) parametry domyślne, RAID 10
        SystemParams d = new SystemParams();
        print("Domyślne, 100 realizacji, seed 42", new Summary(Experiment.runMany(d, 100, 42)));

        // 2) podkręcony kontroler (jak w kroku 3)
        SystemParams p = new SystemParams();
        p.ctrlMttf = 500;
        p.upsMttf = 1e12;
        p.switchMttf = 1e12;
        p.serverEta = 1e12;
        p.diskEta = 1e12;
        print("Kontroler 500 h, 1000 realizacji, seed 7", new Summary(Experiment.runMany(p, 1000, 7)));
    }
}