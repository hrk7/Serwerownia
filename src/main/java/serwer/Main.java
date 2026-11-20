package serwer;

import serwer.model.*;

public class Main {

    static boolean check(SystemModel m, int... failed) {
        boolean[] up = new boolean[m.components().size()];
        java.util.Arrays.fill(up, true);
        for (int i : failed) up[i] = false;
        return m.works(up);
    }

    public static void main(String[] args) {
        SystemParams p = new SystemParams();          // domyślnie RAID 10
        SystemModel m = new SystemModel(p);

        System.out.println("Liczba elementów: " + m.components().size());
        System.out.println("wszystko sprawne:          " + check(m));
        System.out.println("padł 1 UPS:                " + check(m, 0));
        System.out.println("padły oba UPS:             " + check(m, 0, 1));
        System.out.println("padły 2 serwery:           " + check(m, 4, 5));
        System.out.println("padł 1 serwer:             " + check(m, 4));
        System.out.println("padł kontroler:            " + check(m, 7));
        System.out.println("RAID10: dyski 1 i 2 (para):  " + check(m, 8, 9));
        System.out.println("RAID10: dyski 1 i 3 (różne): " + check(m, 8, 10));
        System.out.println("RAID10: dyski 1, 3 i 5:      " + check(m, 8, 10, 12));

        p.storage = StorageType.RAID6;
        SystemModel m6 = new SystemModel(p);
        System.out.println("RAID6:  dyski 1 i 2:         " + check(m6, 8, 9));
        System.out.println("RAID6:  dyski 1, 2 i 3:      " + check(m6, 8, 9, 10));
    }
}
