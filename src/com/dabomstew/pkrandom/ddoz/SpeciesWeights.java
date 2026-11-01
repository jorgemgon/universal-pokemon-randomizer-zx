package com.dabomstew.pkrandom.ddoz;

import com.dabomstew.pkrandom.pokemon.*;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

import java.util.*;

/** Daily Dose species weighting for UPR-ZX. */
public final class SpeciesWeights {

    public enum Scope { LAB, EARLY, ALL }

    private static boolean enabled = false;
    private static double power = 2.5;
    private static int cap = 560;
    private static Scope biasScope = Scope.EARLY;
    private static final Map<Type, Double> typeBiases = new EnumMap<>(Type.class);
    private static Map<Integer, List<MoveLearnt>> movesets = Collections.emptyMap();
    private static List<Move> moveData = Collections.emptyList();

    private SpeciesWeights() {
    }

    public static void configure(RomHandler rom, double weightPower, int bstCap,
                                 Map<Type, Double> biases, Scope scope) {
        power = weightPower > 0 ? weightPower : 2.5;
        cap = bstCap > 200 ? bstCap : 560;
        biasScope = scope == null ? Scope.EARLY : scope;
        typeBiases.clear();
        if (biases != null) {
            typeBiases.putAll(biases);
        }
        movesets = rom.getMovesLearnt();
        moveData = rom.getMoves();
        enabled = true;
    }

    public static void reset() {
        enabled = false;
        typeBiases.clear();
        movesets = Collections.emptyMap();
        moveData = Collections.emptyList();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isEarlyArea(EncounterSet area) {
        if (area == null || area.displayName == null) {
            return false;
        }
        String name = area.displayName.toLowerCase(Locale.ROOT);
        return name.startsWith("route 1 ")
                || name.startsWith("route 2 ")
                || name.startsWith("route 22 ")
                || name.startsWith("viridian city")
                || name.startsWith("viridian forest");
    }

    public static double weight(Pokemon p, boolean early, boolean lab) {
        int bst = p.hp + p.attack + p.defense + p.spatk + p.spdef + p.speed;
        double bstNorm = clamp((Math.min(bst, cap) - 200.0) / (cap - 200.0));
        double offense = Math.max(p.attack, p.spatk);
        double spread = offense + p.speed + 0.5 * (p.hp + p.defense + p.spdef);
        double spreadNorm = clamp((spread - 120.0) / 300.0);
        double movesNorm = movesNorm(p);

        double quality = 0.55 * bstNorm + 0.25 * spreadNorm + 0.20 * movesNorm;
        double base = (early || lab) ? quality : bstNorm;
        double weight = Math.pow(0.15 + 0.85 * base, power);

        boolean applyBias = biasScope == Scope.ALL
                || (biasScope == Scope.EARLY && early)
                || (biasScope == Scope.LAB && lab);
        if (applyBias) {
            for (Map.Entry<Type, Double> bias : typeBiases.entrySet()) {
                if (p.primaryType == bias.getKey() || p.secondaryType == bias.getKey()) {
                    weight *= bias.getValue();
                }
            }
        }
        return Math.max(weight, 1e-6);
    }

    public static Pokemon pick(List<Pokemon> pool, Random random, boolean early, boolean lab) {
        List<Pokemon> candidates = new ArrayList<>(pool);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Tried to choose a weighted species from an empty set!");
        }
        candidates.sort(Comparator.comparingInt(pk -> pk.number));

        double total = 0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] = weight(candidates.get(i), early, lab);
            total += weights[i];
        }
        double draw = random.nextDouble() * total;
        for (int i = 0; i < candidates.size(); i++) {
            draw -= weights[i];
            if (draw <= 0) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static double movesNorm(Pokemon p) {
        List<MoveLearnt> learnt = movesets.get(p.number);
        if (learnt == null) {
            return 0;
        }
        double best = 0;
        for (MoveLearnt entry : learnt) {
            if (entry.level < 1 || entry.level > 15) {
                continue;
            }
            if (entry.move <= 0 || entry.move >= moveData.size()) {
                continue;
            }
            Move move = moveData.get(entry.move);
            if (move == null || move.power <= 0) {
                continue;
            }
            double accuracy = move.hitratio <= 0 ? 1.0 : Math.min(1.0, move.hitratio / 100.0);
            best = Math.max(best, move.power * accuracy / 100.0);
        }
        return clamp(best / 120.0);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
