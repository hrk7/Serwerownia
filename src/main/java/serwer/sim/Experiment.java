package serwer.sim;

import serwer.model.SystemModel;
import serwer.model.SystemParams;

import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

public final class Experiment {

    private Experiment() {
    }

    /** Wynik jest powtarzalny dla danego seed. */
    public static List<RunResult> runMany(SystemParams p, int runs, long seed) {
        SystemModel model = new SystemModel(p);
        SplittableRandom master = new SplittableRandom(seed);
        List<SplittableRandom> rngs = IntStream.range(0, runs).mapToObj(i -> master.split()).toList();
        return IntStream.range(0, runs).parallel()
                .mapToObj(i -> Simulation.run(p, model, rngs.get(i)))
                .toList();
    }
}