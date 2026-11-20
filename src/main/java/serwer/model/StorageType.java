package serwer.model;

public enum StorageType {
    RAID5("RAID 5"), RAID6("RAID 6"), RAID10("RAID 10");

    private final String label;

    StorageType(String label) { this.label = label; }

    /** phi_pam = x_ctrl * [macierz działa] */
    public boolean works(boolean ctrlUp, boolean[] diskUp) {
        if (!ctrlUp) return false;
        int up = 0;
        for (boolean d : diskUp) if (d) up++;
        return switch (this) {
            case RAID5 -> up >= diskUp.length - 1;
            case RAID6 -> up >= diskUp.length - 2;
            case RAID10 -> allPairsAlive(diskUp);
        };
    }

    private static boolean allPairsAlive(boolean[] d) {
        for (int i = 0; i + 1 < d.length; i += 2) {
            if (!d[i] && !d[i + 1]) return false;
        }
        return true;
    }

    @Override
    public String toString() { return label; }
}
