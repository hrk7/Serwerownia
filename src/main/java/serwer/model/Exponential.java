package serwer.model;

import java.util.random.RandomGenerator;

public record Exponential(double mean) implements Distribution {
    public double sample(RandomGenerator rng) {
        return mean == 0 ? 0 : -mean * Math.log(1.0 - rng.nextDouble());
    }
}
