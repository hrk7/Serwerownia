package serwer.model;

import java.util.random.RandomGenerator;

public interface Distribution {
    double sample(RandomGenerator rng);   // czas w godzinach
}
