package com.dabomstew.pkrandom.ddoz;

import com.dabomstew.pkrandom.FileFunctions;
import com.dabomstew.pkrandom.RandomSource;
import com.dabomstew.pkrandom.Randomizer;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.pokemon.Move;
import com.dabomstew.pkrandom.pokemon.MoveLearnt;
import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.romhandlers.AbstractRomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen3RomHandler;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Emits the markdown seed-test table: starters, stats, abilities, weights and starting moves. */
public final class DdozReport {

    public static void main(String[] args) throws Exception {
        String rom = args[0];
        String ips = args[1];
        String outDir = args[2];
        Map<com.dabomstew.pkrandom.pokemon.Type, Double> biases = new EnumMap<>(com.dabomstew.pkrandom.pokemon.Type.class);
        SpeciesWeights.Scope scope = SpeciesWeights.Scope.EARLY;
        List<Long> seedList = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            if ("--bias".equals(args[i])) {
                String value = args[++i];
                int eq = value.indexOf('=');
                biases.put(com.dabomstew.pkrandom.pokemon.Type.valueOf(value.substring(0, eq).toUpperCase()),
                        Double.parseDouble(value.substring(eq + 1)));
            } else if ("--scope".equals(args[i])) {
                scope = SpeciesWeights.Scope.valueOf(args[++i].toUpperCase());
            } else {
                seedList.add(Long.parseLong(args[i]));
            }
        }
        long[] seeds = new long[seedList.size()];
        for (int i = 0; i < seeds.length; i++) seeds[i] = seedList.get(i);
        System.out.println("bias=" + biases + " scope=" + scope);

        Settings settings;
        try (InputStream in = DdozReport.class.getResourceAsStream("kaizo-v1.rnqs")) {
            Path preset = Files.createTempFile("ddoz-report-preset-", ".rnqs");
            Files.copy(in, preset, StandardCopyOption.REPLACE_EXISTING);
            try (FileInputStream fis = new FileInputStream(preset.toFile())) {
                settings = Settings.read(fis);
            }
            Files.deleteIfExists(preset);
        }
        settings.setCustomNames(FileFunctions.getCustomNames());
        settings.setUseMinimumCatchRate(true);
        settings.setMinimumCatchRateLevel(5);

        byte[] source = Files.readAllBytes(Paths.get(rom));
        byte[] patch = Files.readAllBytes(Paths.get(ips));
        Path base = Files.createTempFile("ddoz-report-base-", ".gba");
        Files.write(base, IpsPatcher.apply(source, patch));

        System.out.println("| Seed | Starter | Types | BST | Ability | Weight | Type bias | Starting moves (L1) | ROM SHA-1 |");
        System.out.println("|---:|---|---|---:|---|---:|---:|---|---|");

        for (long seed : seeds) {
            RomHandler handler = new Gen3RomHandler.Factory().create(RandomSource.instance());
            handler.loadRom(base.toString());
            ((AbstractRomHandler) handler).checkPokemonRestrictions();
            SpeciesWeights.configure(handler, 2.5, 560, biases, scope);

            Path out = Paths.get(outDir, "report-" + seed + ".gba");
            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            Randomizer randomizer = new Randomizer(settings, handler,
                    ResourceBundle.getBundle("com/dabomstew/pkrandom/newgui/Bundle"), false);
            randomizer.randomize(out.toString(), new PrintStream(sink, false, "UTF-8"), seed);

            String sha1 = sha1(out);
            Map<Integer, List<MoveLearnt>> learnt = handler.getMovesLearnt();
            List<Move> moves = handler.getMoves();
            List<Pokemon> starters = handler.getStarters();
            for (int i = 0; i < starters.size(); i++) {
                Pokemon p = starters.get(i);
                StringBuilder types = new StringBuilder(p.primaryType.toString());
                if (p.secondaryType != null && p.secondaryType != p.primaryType) {
                    types.append('/').append(p.secondaryType);
                }
                int bst = p.hp + p.attack + p.defense + p.spatk + p.spdef + p.speed;
                String ability = ((AbstractRomHandler) handler).abilityName(p.ability1);
                if (p.ability2 > 0 && p.ability2 != p.ability1) {
                    ability += " / " + ((AbstractRomHandler) handler).abilityName(p.ability2);
                }
                List<String> moveNames = new ArrayList<>();
                List<MoveLearnt> list = learnt.get(p.number);
                if (list != null) {
                    for (MoveLearnt ml : list) {
                        if (ml.level == 1 && ml.move > 0 && ml.move < moves.size()) {
                            moveNames.add(moves.get(ml.move).name + " (L" + ml.level + ")");
                        }
                    }
                }
                double weight = SpeciesWeights.weight(p, false, true);
                System.out.printf(Locale.ROOT, "| %d | %s | %s | %d | %s | %.3f | %s | %s | %s |%n",
                        seed, p.name, types, bst, ability, weight, biasFor(p, biases),
                        String.join(", ", moveNames), i == 0 ? sha1 : "");
            }
            Files.deleteIfExists(out);
            Files.deleteIfExists(Paths.get(out.toString() + ".log"));
        }
        Files.deleteIfExists(base);
    }

    private static String biasFor(Pokemon p, Map<com.dabomstew.pkrandom.pokemon.Type, Double> biases) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<com.dabomstew.pkrandom.pokemon.Type, Double> e : biases.entrySet()) {
            if (p.primaryType == e.getKey() || p.secondaryType == e.getKey()) {
                if (sb.length() > 0) sb.append(" x ");
                sb.append(e.getKey()).append("x").append(e.getValue());
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static String sha1(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(path));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
