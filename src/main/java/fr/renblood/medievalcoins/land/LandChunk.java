package fr.renblood.medievalcoins.land;

import java.util.Objects;

public class LandChunk {
    private int x;
    private int z;

    public LandChunk() {
    }

    public LandChunk(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LandChunk other)) {
            return false;
        }
        return x == other.x && z == other.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return x + "," + z;
    }
}
