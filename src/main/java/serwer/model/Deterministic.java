package serwer.model;

import java.util.random.RandomGenerator;

public record Deterministic(double value) implements Distribution {
    public double sample(RandomGenerator rng) { return value; }
}
