package serwer.model;

public record Component(String name, Block block, Distribution lifetime, Distribution repair) {
    public enum Block { POWER, NETWORK, SERVERS, CONTROLLER, DISKS }
}
