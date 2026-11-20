package serwer;

import serwer.model.*;
import java.util.SplittableRandom;

public class Main {
    public static void main(String[] args) {
        SplittableRandom rng = new SplittableRandom(42);
        int n = 1_000_000;
        Distribution[] d = {
                new Exponential(4),
                new Weibull(1.0, 60000),
                new Weibull(1.5, 60000)
        };
        for (Distribution x : d) {
            double sum = 0;
            for (int i = 0; i < n; i++) sum += x.sample(rng);
            System.out.println(x + " -> średnia = " + sum / n);
        }
    }
}
