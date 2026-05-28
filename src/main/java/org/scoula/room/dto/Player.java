package org.scoula.room.dto;

public record Player(String id, String name) {

    @Override
    public boolean equals(Object o) {
        return o instanceof Player p && id.equals(p.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
