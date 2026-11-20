package serwer.sla;

import java.util.List;

/**
 * Poziom umowy SLA (Tabela 2 i Rys. 7.4). Progi są ostre (> lub <), tak jak w tabeli.
 * Długość przerwy w okresach 10-minutowych, średni czas naprawy w godzinach.
 */
public record SlaLevel(String name,
                       double minAvailability,
                       int maxOutages,
                       int maxOutageLenPeriods,
                       double maxMeanRepairH,
                       double reactionTimeH,
                       double maintenanceCost,
                       double fee,
                       double penalty) {

    // Koszty dla standard z przykładu w specyfikacji: 430 000 / 535 000 / 100 000.
    // TODO: uzupełnij koszt utrzymania, opłatę i karę dla premium i ultra premium z Rys. 7.4.
    public static final SlaLevel STANDARD =
            new SlaLevel("standard", 0.95, 52, 200, 50, 24, 430_000, 535_000, 100_000);
    public static final SlaLevel PREMIUM =
            new SlaLevel("premium", 0.99, 22, 70, 24, 8, 0, 0, 0);
    public static final SlaLevel ULTRA_PREMIUM =
            new SlaLevel("ultra premium", 0.999, 3, 32, 24, 2, 0, 0, 0);

    public static List<SlaLevel> all() {
        return List.of(STANDARD, PREMIUM, ULTRA_PREMIUM);
    }

    @Override
    public String toString() {
        return name;
    }
}