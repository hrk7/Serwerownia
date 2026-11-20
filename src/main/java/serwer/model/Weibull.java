package serwer.model;

import java.util.random.RandomGenerator;

public record Weibull(double beta, double eta) implements Distribution {
    public double sample(RandomGenerator rng) {
        return eta * Math.pow(-Math.log(1.0 - rng.nextDouble()), 1.0 / beta);
    }
}
