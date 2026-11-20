import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/**
 * Symulacja dostepnosci serwerowni (temat 7, umowa SLA) - pamiec masowa RAID 10.
 *
 * Metoda: symulacja zdarzen dyskretnych (kolejka zdarzen) + Monte Carlo
 * (wiele niezaleznych realizacji jednego roku pracy systemu).
 *
 * Uruchomienie:
 *   javac SymulacjaSlaRaid10.java && java SymulacjaSlaRaid10 [liczba_realizacji]
 * albo (Java 11+):
 *   java SymulacjaSlaRaid10.java [liczba_realizacji]
 */
public class SymulacjaSlaRaid10 {

    // =====================================================================
    // KROK 1. PARAMETRY (Tabela 1 i 2 z projektu)
    // =====================================================================
    static final double T_ROK = 8760.0;          // dlugosc symulacji [h], praca ciagla 24/7
    static final double ROZDZ = 10.0 / 60.0;     // rozdzielczosc pomiaru przerw: 10 min [h]
    static int nRealizacji = 100;
    static final long SEED = 2026;

    static final double BETA_SERW = 1.5, ETA_SERW = 60_000.0;   // serwery: Weibull
    static final double BETA_DYSK = 1.8, ETA_DYSK = 50_000.0;   // dyski: Weibull
    static final double MTTF_UPS = 80_000.0, MTTF_SIEC = 100_000.0, MTTF_KTRL = 150_000.0;

    // Zalozenie: dlugosc przerwy w jednostkach 10 min, sredni czas naprawy (MTRS) w godzinach
    // (w PDF brak jednostek z Rys. 7.4).
    static final boolean MTRS_W_GODZINACH = true;

    /** Poziom SLA: progi (Tabela 2) + koszty. null = brak danych z Rys. 7.4. */
    static class Sla {
        final String nazwa;
        final double a;          // minimalna dostepnosc (ostro wieksza)
        final int n;             // liczba przerw (ostro mniejsza)
        final int lmax;          // max dlugosc przerwy [x10 min] (ostro mniejsza)
        final double mtrs;       // sredni czas naprawy (ostro mniejszy)
        final double tReakcji;   // czas reakcji serwisu [h]
        final Double koszt, oplata, kara;

        Sla(String nazwa, double a, int n, int lmax, double mtrs, double tReakcji,
            Double koszt, Double oplata, Double kara) {
            this.nazwa = nazwa; this.a = a; this.n = n; this.lmax = lmax;
            this.mtrs = mtrs; this.tReakcji = tReakcji;
            this.koszt = koszt; this.oplata = oplata; this.kara = kara;
        }
    }

    static final Sla[] SLA = {
            new Sla("standard",      0.95,  52, 200, 50, 24.0, 430_000.0, 535_000.0, 100_000.0),
            // Premium i ultra premium: uzupelnij koszt, oplate i kare z Rys. 7.4 (zamiast null)
            new Sla("premium",       0.99,  22,  70, 24,  8.0, null, null, null),
            new Sla("ultra premium", 0.999,  3,  32, 24,  2.0, null, null, null),
    };

    // =====================================================================
    // KROK 2. MODEL ELEMENTOW: rozklad czasu zycia i czasu naprawy
    // =====================================================================
    static class Element {
        final String nazwa;
        final ToDoubleFunction<Random> zycie;     // losuje czas zycia [h]
        final ToDoubleFunction<Random> naprawa;   // losuje czas naprawy [h]

        Element(String nazwa, ToDoubleFunction<Random> zycie, ToDoubleFunction<Random> naprawa) {
            this.nazwa = nazwa; this.zycie = zycie; this.naprawa = naprawa;
        }
    }

    // 1 - nextDouble() lezy w (0, 1], wiec log nigdy nie dostaje zera.
    static ToDoubleFunction<Random> zycieWykl(double mttf) {
        return r -> -mttf * Math.log(1.0 - r.nextDouble());
    }
    static ToDoubleFunction<Random> zycieWeibull(double beta, double eta) {
        return r -> eta * Math.pow(-Math.log(1.0 - r.nextDouble()), 1.0 / beta);
    }
    static ToDoubleFunction<Random> naprawaWykl(double tR, double srednia) {
        return r -> tR - srednia * Math.log(1.0 - r.nextDouble());
    }
    static ToDoubleFunction<Random> naprawaStala(double tR, double h) {
        return r -> tR + h;
    }

    // Indeksy elementow w wektorze stanu (true = sprawny)
    static final int[] UPS = {0, 1};
    static final int[] SIEC = {2, 3};
    static final int[] SERW = {4, 5, 6};
    static final int KTRL = 7;
    static final int[][] PARY_RAID10 = {{8, 9}, {10, 11}, {12, 13}};  // 3 pary lustrzane

    static Element[] zbudujElementy(double tR) {
        List<Element> el = new ArrayList<>();
        for (int k = 1; k <= 2; k++)
            el.add(new Element("UPS " + k, zycieWykl(MTTF_UPS), naprawaWykl(tR, 4)));
        for (int k = 1; k <= 2; k++)
            el.add(new Element("Przelacznik " + k, zycieWykl(MTTF_SIEC), naprawaWykl(tR, 4)));
        for (int k = 1; k <= 3; k++)
            el.add(new Element("Serwer " + k, zycieWeibull(BETA_SERW, ETA_SERW), naprawaWykl(tR, 6)));
        el.add(new Element("Kontroler RAID", zycieWykl(MTTF_KTRL), naprawaWykl(tR, 4)));
        for (int k = 1; k <= 6; k++)
            el.add(new Element("Dysk " + k, zycieWeibull(BETA_DYSK, ETA_DYSK), naprawaStala(tR, 12)));
        return el.toArray(new Element[0]);
    }

    // =====================================================================
    // KROK 3. FUNKCJA STRUKTURY phi_sys (wzor (1), RAID 10 zamiast RAID 6)
    // =====================================================================
    static boolean phi(boolean[] x) {
        boolean zas  = x[UPS[0]] || x[UPS[1]];                          // 1 z 2
        boolean siec = x[SIEC[0]] || x[SIEC[1]];                        // 1 z 2
        int sprawneSerwery = (x[SERW[0]] ? 1 : 0) + (x[SERW[1]] ? 1 : 0) + (x[SERW[2]] ? 1 : 0);
        boolean serw = sprawneSerwery >= 2;                             // 2 z 3
        boolean raid10 = true;
        for (int[] p : PARY_RAID10) raid10 &= (x[p[0]] || x[p[1]]);     // >= 1 dysk w kazdej parze
        boolean pam = x[KTRL] && raid10;
        return zas && siec && serw && pam;
    }

    // =====================================================================
    // KROK 4. JEDNA REALIZACJA (1 rok) - kolejka zdarzen
    // =====================================================================
    static class Zdarzenie {
        final double t;
        final int idx;
        final boolean uszkodzenie;   // true = uszkodzenie, false = koniec naprawy

        Zdarzenie(double t, int idx, boolean uszkodzenie) {
            this.t = t; this.idx = idx; this.uszkodzenie = uszkodzenie;
        }
    }

    static List<Double> symulujRok(Element[] el, Random rng) {
        int n = el.length;
        boolean[] x = new boolean[n];
        PriorityQueue<Zdarzenie> kolejka = new PriorityQueue<>((a, b) -> Double.compare(a.t, b.t));

        // kazdy element dostaje wylosowany czas zycia -> zdarzenie "uszkodzenie"
        for (int i = 0; i < n; i++) {
            x[i] = true;
            kolejka.add(new Zdarzenie(el[i].zycie.applyAsDouble(rng), i, true));
        }

        List<Double> przerwy = new ArrayList<>();   // dlugosci przerw [h]
        double poczatek = -1.0;                     // <0 = brak otwartej przerwy

        while (!kolejka.isEmpty()) {
            Zdarzenie z = kolejka.poll();
            if (z.t >= T_ROK) break;

            if (z.uszkodzenie) {
                x[z.idx] = false;
                kolejka.add(new Zdarzenie(z.t + el[z.idx].naprawa.applyAsDouble(rng), z.idx, false));
            } else {   // koniec naprawy -> element "jak nowy", nowy czas zycia
                x[z.idx] = true;
                kolejka.add(new Zdarzenie(z.t + el[z.idx].zycie.applyAsDouble(rng), z.idx, true));
            }

            boolean sprawny = phi(x);
            if (!sprawny && poczatek < 0) {              // 1 -> 0: otworz przerwe
                poczatek = z.t;
            } else if (sprawny && poczatek >= 0) {       // 0 -> 1: zamknij przerwe
                przerwy.add(z.t - poczatek);
                poczatek = -1.0;
            }
        }
        if (poczatek >= 0) przerwy.add(T_ROK - poczatek);   // przerwa trwa w chwili konca roku
        return przerwy;
    }

    // =====================================================================
    // KROK 5. MIARY Z JEDNEJ REALIZACJI
    // =====================================================================
    static class Miary {
        double a, mtrsH, mtbf, mtbsi;
        int n, lmax;
    }

    static Miary miary(List<Double> przerwy) {
        Miary m = new Miary();
        m.n = przerwy.size();
        double przestoj = 0.0;
        int lmax = 0;
        for (double d : przerwy) {
            przestoj += d;
            lmax = Math.max(lmax, (int) Math.ceil(d / ROZDZ - 1e-9));   // w okresach 10 min
        }
        m.a = 1.0 - przestoj / T_ROK;
        m.lmax = lmax;
        m.mtrsH = m.n > 0 ? przestoj / m.n : 0.0;
        m.mtbf = m.n > 0 ? (T_ROK - przestoj) / m.n : Double.POSITIVE_INFINITY;
        m.mtbsi = m.mtbf + m.mtrsH;
        return m;
    }

    /** Zwraca [A, N, Lmax, MTRS] - czy dany warunek SLA jest spelniony. */
    static boolean[] sprawdzSla(Miary m, Sla p) {
        double mtrs = MTRS_W_GODZINACH ? m.mtrsH : m.mtrsH / ROZDZ;
        return new boolean[] {m.a > p.a, m.n < p.n, m.lmax < p.lmax, mtrs < p.mtrs};
    }

    /** 95% przedzial ufnosci Wilsona dla proporcji k/n. */
    static double[] wilson(int k, int n) {
        if (n == 0) return new double[] {0.0, 0.0};
        double z = 1.96, p = (double) k / n, d = 1 + z * z / n;
        double c = (p + z * z / (2.0 * n)) / d;
        double h = z * Math.sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / d;
        return new double[] {Math.max(0.0, c - h), Math.min(1.0, c + h)};
    }

    // =====================================================================
    // KROK 6. MONTE CARLO: wiele realizacji, prawdopodobienstwa i koszty
    // =====================================================================
    static void eksperyment(Sla p) {
        Element[] el = zbudujElementy(p.tReakcji);
        Random rng = new Random(SEED);
        int R = nRealizacji;
        int[] zgodne = new int[5];   // A, N, Lmax, MTRS, wszystkie
        double sumaA = 0, sumaN = 0, sumaMtrsLat = 0;
        int bezPrzerw = 0, latZPrzerwa = 0, maxLmax = 0;

        for (int r = 0; r < R; r++) {
            Miary m = miary(symulujRok(el, rng));
            sumaA += m.a;
            sumaN += m.n;
            maxLmax = Math.max(maxLmax, m.lmax);
            if (m.n == 0) bezPrzerw++;
            else { latZPrzerwa++; sumaMtrsLat += m.mtrsH; }

            boolean[] ok = sprawdzSla(m, p);
            boolean wszystkie = true;
            for (int k = 0; k < 4; k++) {
                if (ok[k]) zgodne[k]++;
                wszystkie &= ok[k];
            }
            if (wszystkie) zgodne[4]++;
        }

        double[] P = new double[5];
        for (int k = 0; k < 5; k++) P[k] = (double) zgodne[k] / R;
        double[] ci = wilson(zgodne[4], R);

        System.out.printf("%n=== SLA: %s  (t_reakcji = %.0f h, RAID 10, %d realizacji) ===%n",
                p.nazwa.toUpperCase(), p.tReakcji, R);
        System.out.printf("  srednia dostepnosc A       : %.6f%n", sumaA / R);
        System.out.printf("  srednia liczba przerw N    : %.3f   (lata bez przerw: %.1f%%)%n",
                sumaN / R, 100.0 * bezPrzerw / R);
        System.out.printf("  najdluzsza przerwa (max)   : %d x 10 min%n", maxLmax);
        if (latZPrzerwa > 0)
            System.out.printf("  MTRS (lata z przerwa) [h]  : %.2f%n", sumaMtrsLat / latZPrzerwa);
        System.out.println("  P(spelnienia warunku):");
        System.out.printf("     A > %.3f%%   : %.3f%n", p.a * 100, P[0]);
        System.out.printf("     N < %d        : %.3f%n", p.n, P[1]);
        System.out.printf("     Lmax < %d    : %.3f%n", p.lmax, P[2]);
        System.out.printf("     MTRS < %.0f    : %.3f%n", p.mtrs, P[3]);
        System.out.printf("  P(wszystkie naraz)         : %.3f   95%% CI Wilsona: [%.3f; %.3f]  -> %s%n",
                P[4], ci[0], ci[1], P[4] >= 0.9 ? ">= 90% (OK)" : "< 90%");

        if (p.koszt != null) {
            double eKara = (1 - P[4]) * p.kara;   // kara raz w roku przy zlamaniu SLA
            double bilans = p.oplata - p.koszt - eKara;
            System.out.printf("  koszt utrzymania           : %,.0f%n", p.koszt);
            System.out.printf("  oczekiwana kara            : %,.0f%n", eKara);
            System.out.printf("  koszt dostawcy lacznie     : %,.0f%n", p.koszt + eKara);
            System.out.printf("  bilans dostawcy (oplata-koszt-kara): %,.0f%n", bilans);
        } else {
            System.out.println("  koszty: brak danych z Rys. 7.4 (uzupelnij tablice SLA)");
        }
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        if (args.length > 0) nRealizacji = Integer.parseInt(args[0]);
        for (Sla p : SLA) eksperyment(p);
    }
}