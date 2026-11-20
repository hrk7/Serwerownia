package serwer.model;

import serwer.model.Component.Block;

import java.util.ArrayList;
import java.util.List;

public final class SystemModel {

    private final List<Component> components = new ArrayList<>();
    private final SystemParams p;
    private final int upsFrom, switchFrom, serverFrom, ctrlIdx, diskFrom;

    public SystemModel(SystemParams p) {
        p.validate();
        this.p = p;

        upsFrom = components.size();
        for (int i = 1; i <= p.upsCount; i++)
            components.add(new Component("UPS " + i, Block.POWER,
                    new Exponential(p.upsMttf), new Exponential(p.upsRepairMean)));

        switchFrom = components.size();
        for (int i = 1; i <= p.switchCount; i++)
            components.add(new Component("Przełącznik " + i, Block.NETWORK,
                    new Exponential(p.switchMttf), new Exponential(p.switchRepairMean)));

        serverFrom = components.size();
        for (int i = 1; i <= p.serverCount; i++)
            components.add(new Component("Serwer " + i, Block.SERVERS,
                    new Weibull(p.serverBeta, p.serverEta), new Exponential(p.serverRepairMean)));

        ctrlIdx = components.size();
        components.add(new Component("Kontroler RAID", Block.CONTROLLER,
                new Exponential(p.ctrlMttf), new Exponential(p.ctrlRepairMean)));

        diskFrom = components.size();
        for (int i = 1; i <= p.diskCount; i++)
            components.add(new Component("Dysk " + i, Block.DISKS,
                    new Weibull(p.diskBeta, p.diskEta), new Deterministic(p.diskRebuildH)));
    }

    public List<Component> components() { return components; }

    /** phi_sys: true = system działa. up[i] = stan i-tego elementu z listy components(). */
    public boolean works(boolean[] up) {
        return countUp(up, upsFrom, p.upsCount) >= p.upsRequired
                && countUp(up, switchFrom, p.switchCount) >= p.switchRequired
                && countUp(up, serverFrom, p.serverCount) >= p.serversRequired
                && storageWorks(up);
    }

    private boolean storageWorks(boolean[] up) {
        boolean[] disks = new boolean[p.diskCount];
        System.arraycopy(up, diskFrom, disks, 0, p.diskCount);
        return p.storage.works(up[ctrlIdx], disks);
    }

    private static int countUp(boolean[] up, int from, int n) {
        int c = 0;
        for (int i = from; i < from + n; i++) if (up[i]) c++;
        return c;
    }
}