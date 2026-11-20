package serwer.sla;

/** Prawdopodobieństwa spełnienia SLA oszacowane z wielu realizacji oraz bilans kosztów. */
public record SlaSummary(SlaLevel level, int runs,
                         double pAvailability, double pOutages, double pLongestOutage, double pMeanRepair,
                         double pAll) {

    /** Czy "szansa na osiągnięcie" przekracza próg (w planie eksperymentów: 90%). */
    public boolean meetsTarget(double threshold) {
        return pAll >= threshold;
    }

    /** Zakładamy: kara raz w roku, gdy nie spełniono wszystkich warunków. */
    public double expectedPenalty() {
        return level.penalty() * (1.0 - pAll);
    }

    public double providerCost() {
        return level.maintenanceCost() + expectedPenalty();
    }

    /** Opłata klienta minus koszt utrzymania minus oczekiwana kara. */
    public double providerBalance() {
        return level.fee() - providerCost();
    }
}