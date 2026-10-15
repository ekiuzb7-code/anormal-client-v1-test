package com.anormal.client.util;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Greedy spatial clustering shared by the structure finders.
 *
 * <p>Each point joins the nearest existing cluster whose centroid is within
 * {@code radius}; otherwise it starts a new one. A single pass is enough
 * because the finders feed in blocks that are already grouped by structure, and
 * it keeps the cost at {@code O(points x clusters)} instead of the
 * {@code O(points^3)} the old "grow a group by rescanning everything" loops
 * could reach.</p>
 */
public final class Clusterer {
    private Clusterer() {}

    public static final class Cluster {
        public final double x, y, z;
        public final int size;

        Cluster(double x, double y, double z, int size) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.size = size;
        }

        public BlockPos center() {
            return new BlockPos((int) Math.round(x), (int) Math.round(y), (int) Math.round(z));
        }
    }

    /**
     * @param points  world positions to group
     * @param radius  join radius, in blocks
     * @param minSize clusters smaller than this are dropped
     * @param limit   stop after this many clusters
     */
    public static List<Cluster> cluster(Collection<BlockPos> points, double radius, int minSize, int limit) {
        List<double[]> sums = new ArrayList<>();   // {x, y, z, count}
        double r2 = radius * radius;

        for (BlockPos p : points) {
            int best = -1;
            double bestD = r2;
            for (int i = 0; i < sums.size(); i++) {
                double[] s = sums.get(i);
                double cx = s[0] / s[3], cy = s[1] / s[3], cz = s[2] / s[3];
                double dx = p.getX() - cx, dy = p.getY() - cy, dz = p.getZ() - cz;
                double d = dx * dx + dy * dy + dz * dz;
                if (d <= bestD) {
                    bestD = d;
                    best = i;
                }
            }
            if (best >= 0) {
                double[] s = sums.get(best);
                s[0] += p.getX();
                s[1] += p.getY();
                s[2] += p.getZ();
                s[3] += 1;
            } else {
                sums.add(new double[]{p.getX(), p.getY(), p.getZ(), 1});
            }
        }

        List<Cluster> out = new ArrayList<>();
        for (double[] s : sums) {
            int n = (int) s[3];
            if (n < minSize) continue;
            out.add(new Cluster(s[0] / n, s[1] / n, s[2] / n, n));
        }
        out.sort((a, b) -> Integer.compare(b.size, a.size));
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }
}
