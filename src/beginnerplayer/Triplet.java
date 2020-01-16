package beginnerplayer;

import battlecode.common.MapLocation;

import java.util.ArrayList;

public class Triplet implements Comparable{
    private ArrayList<MapLocation> path;
    private MapLocation to;
    private int heuristic;

    public Triplet(ArrayList<MapLocation> path, MapLocation to, int heuristic) {
        this.path = path;
        this.to = to;
        this.heuristic = heuristic;
    }

    public ArrayList<MapLocation> getPath() {
        return path;
    }
    public MapLocation getTo() {
        return to;
    }
    public int getHeuristic() {
        return heuristic;
    }

    @Override
    public int compareTo(Object o) {
        if (o == null) throw new NullPointerException();
        return heuristic - ((Triplet) o).getHeuristic();
    }
}
