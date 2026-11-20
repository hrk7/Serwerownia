package serwer.sim;

import serwer.model.Component;
import serwer.model.SystemModel;
import serwer.model.SystemParams;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.random.RandomGenerator;

public final class Simulation {

    private enum Type { FAILURE, REPAIR_END }

    private record Event(double time, int component, Type type) implements Comparable<Event> {
        @Override
        public int compareTo(Event o) {
            return Double.compare(time, o.time);
        }
    }

    private Simulation() {
    }

    public static RunResult run(SystemParams p, RandomGenerator rng) {
        return run(p, new SystemModel(p), rng);
    }

    public static RunResult run(SystemParams p, SystemModel model, RandomGenerator rng) {
        List<Component> comps = model.components();
        int n = comps.size();
        boolean[] up = new boolean[n];
        PriorityQueue<Event> queue = new PriorityQueue<>();

        // krok 2: dla każdego elementu losujemy czas życia i wpisujemy uszkodzenie do kolejki
        for (int i = 0; i < n; i++) {
            up[i] = true;
            queue.add(new Event(comps.get(i).lifetime().sample(rng), i, Type.FAILURE));
        }

        List<Double> outages = new ArrayList<>();
        boolean systemUp = true;
        double outageStart = 0;

        // kroki 3-5: pobieramy najbliższe zdarzenie aż do końca roku
        while (!queue.isEmpty()) {
            Event e = queue.poll();
            if (e.time() >= p.horizonH) break;

            Component c = comps.get(e.component());
            if (e.type() == Type.FAILURE) {
                up[e.component()] = false;
                double repair = p.reactionTimeH + c.repair().sample(rng);
                queue.add(new Event(e.time() + repair, e.component(), Type.REPAIR_END));
            } else {
                up[e.component()] = true;
                queue.add(new Event(e.time() + c.lifetime().sample(rng), e.component(), Type.FAILURE));
            }

            // krok 4: po każdym zdarzeniu liczymy phi_sys
            boolean nowUp = model.works(up);
            if (systemUp && !nowUp) {
                outageStart = e.time();                 // 1 -> 0: otwarcie przerwy
            } else if (!systemUp && nowUp) {
                outages.add(e.time() - outageStart);    // 0 -> 1: zamknięcie przerwy
            }
            systemUp = nowUp;
        }

        if (!systemUp) outages.add(p.horizonH - outageStart);   // przerwa ucięta końcem roku

        double[] arr = outages.stream().mapToDouble(Double::doubleValue).toArray();
        return new RunResult(p.horizonH, arr);
    }
}