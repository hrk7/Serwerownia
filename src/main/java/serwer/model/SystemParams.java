package serwer.model;

public class SystemParams implements Cloneable {

    public double horizonH = 8760;                 // 1 rok

    public int upsCount = 2, upsRequired = 1;
    public double upsMttf = 80_000, upsRepairMean = 4;

    public int switchCount = 2, switchRequired = 1;
    public double switchMttf = 100_000, switchRepairMean = 4;

    public int serverCount = 3, serversRequired = 2;
    public double serverBeta = 1.5, serverEta = 60_000, serverRepairMean = 6;

    public double ctrlMttf = 150_000, ctrlRepairMean = 4;
    public StorageType storage = StorageType.RAID10;
    public int diskCount = 6;
    public double diskBeta = 1.8, diskEta = 50_000;
    public double diskRebuildH = 12;               // stała wymiana + odbudowa

    public double reactionTimeH = 8;               // czas reakcji serwisu

    public SystemParams copy() {
        try {
            return (SystemParams) clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public void validate() {
        if (storage == StorageType.RAID10 && diskCount % 2 != 0)
            throw new IllegalArgumentException("RAID 10 wymaga parzystej liczby dysków");
        if (upsRequired > upsCount || switchRequired > switchCount || serversRequired > serverCount)
            throw new IllegalArgumentException("wymagana liczba elementów > liczba elementów");
    }
}