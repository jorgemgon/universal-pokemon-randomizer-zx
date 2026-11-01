package com.dabomstew.pkrandom.ddoz;

import com.dabomstew.pkrandom.FileFunctions;
import com.dabomstew.pkrandom.RandomSource;
import com.dabomstew.pkrandom.Randomizer;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.romhandlers.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Daily Dose CLI on top of UPR-ZX 4.6.1. Keeps the stock randomizer untouched and adds the flags
 * the extension needs: deterministic seed, Faster FireRed IPS, guaranteed catch, ZX log, SHA-1 and
 * a bundled Kaizo preset.
 */
public final class DdozCli {

    private DdozCli() {
    }

    public static void main(String[] args) {
        int code;
        try {
            code = run(args);
        } catch (Exception e) {
            e.printStackTrace();
            code = 1;
        }
        if (code != 0) {
            System.exit(code);
        }
    }

    private static int run(String[] args) throws Exception {
        if (args.length == 0 || !"cli".equals(args[0])) {
            usage();
            return 2;
        }
        String settingsPath = null;
        String source = null;
        String destination = null;
        String baseIps = null;
        String preset = null;
        String settingsString = null;
        long seed = -1;
        boolean guaranteedCatch = false;
        boolean saveLog = false;
        boolean printSha1 = false;
        boolean weights = false;
        double weightPower = 2.5;
        int weightCap = 560;
        com.dabomstew.pkrandom.ddoz.SpeciesWeights.Scope biasScope = com.dabomstew.pkrandom.ddoz.SpeciesWeights.Scope.EARLY;
        java.util.Map<com.dabomstew.pkrandom.pokemon.Type, Double> biases = new java.util.EnumMap<>(com.dabomstew.pkrandom.pokemon.Type.class);
        boolean fingerprints = false;
        boolean doubleCheck = false;
        boolean probe = false;
        int probeSpecies = -1;
        String probeType = null;
        long probeFrom = 0;
        int probeMax = 100;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-s": settingsPath = next(args, ++i); break;
                case "-S": settingsString = next(args, ++i); break;
                case "-i": source = next(args, ++i); break;
                case "-o": destination = next(args, ++i); break;
                case "-z": seed = Long.parseLong(next(args, ++i)); break;
                case "--base-ips": baseIps = next(args, ++i); break;
                case "--guaranteed-catch": guaranteedCatch = true; break;
                case "-l": saveLog = true; break;
                case "--print-sha1": printSha1 = true; break;
                case "--weights-preset": {
                    String value = next(args, ++i);
                    weights = !"none".equalsIgnoreCase(value);
                    break;
                }
                case "--weight-power": weightPower = Double.parseDouble(next(args, ++i)); break;
                case "--weight-cap": weightCap = Integer.parseInt(next(args, ++i)); break;
                case "--type-bias": {
                    String value = next(args, ++i);
                    int eq = value.indexOf('=');
                    if (eq > 0) {
                        biases.put(com.dabomstew.pkrandom.pokemon.Type.valueOf(value.substring(0, eq).toUpperCase()),
                                Double.parseDouble(value.substring(eq + 1)));
                    }
                    break;
                }
                case "--type-bias-scope":
                    biasScope = com.dabomstew.pkrandom.ddoz.SpeciesWeights.Scope
                            .valueOf(next(args, ++i).toUpperCase());
                    break;
                case "--log-format": next(args, ++i); break;
                case "--fingerprints": fingerprints = true; break;
                case "--double-check": doubleCheck = true; break;
                case "--probe": probe = true; break;
                case "--species": probeSpecies = Integer.parseInt(next(args, ++i)); break;
                case "--type": probeType = next(args, ++i); break;
                case "--from": probeFrom = Long.parseLong(next(args, ++i)); break;
                case "--max": probeMax = Integer.parseInt(next(args, ++i)); break;
                case "--print-preset": preset = next(args, ++i); break;
                default:
                    System.err.println("Unknown argument: " + arg);
                    usage();
                    return 2;
            }
        }

        if (preset != null) {
            return printPreset(preset);
        }

        if (probe) {
            if (source == null) {
                System.err.println("ERROR: --probe requires -i <rom>");
                return 2;
            }
            Settings probeSettings;
            try {
                probeSettings = loadSettings(settingsPath, settingsString, preset);
            } catch (Exception e) {
                System.err.println("ERROR: " + e.getMessage());
                return 1;
            }
            probeSettings.setCustomNames(FileFunctions.getCustomNames());
            final int wantSpecies = probeSpecies;
            final String wantType = probeType;
            RomHandler.Factory[] probeHandlers = new RomHandler.Factory[] {
                    new Gen3RomHandler.Factory()
            };
            for (long s = probeFrom; s < probeFrom + probeMax; s++) {
                RomHandler probeHandler = probeHandlers[0].create(RandomSource.instance());
                probeHandler.loadRom(source);
                ((com.dabomstew.pkrandom.romhandlers.AbstractRomHandler) probeHandler).checkPokemonRestrictions();
                if (weights) {
                    com.dabomstew.pkrandom.ddoz.SpeciesWeights.configure(probeHandler, weightPower, weightCap, biases, biasScope);
                }
                Path probeOut = Files.createTempFile("ddoz-probe-", ".gba");
                Randomizer probeRandomizer = new Randomizer(probeSettings, probeHandler,
                        java.util.ResourceBundle.getBundle("com/dabomstew/pkrandom/newgui/Bundle"), false);
                probeRandomizer.randomize(probeOut.toString(), new PrintStream(new ByteArrayOutputStream()), s);
                List<com.dabomstew.pkrandom.pokemon.Pokemon> picked = probeHandler.getStarters();
                Files.deleteIfExists(probeOut);
                Files.deleteIfExists(Paths.get(probeOut.toString() + ".log"));
                boolean match = true;
                if (wantSpecies > 0) {
                    match = picked.stream().anyMatch(p -> p.number == wantSpecies);
                }
                if (wantType != null) {
                    com.dabomstew.pkrandom.pokemon.Type t =
                            com.dabomstew.pkrandom.pokemon.Type.valueOf(wantType.toUpperCase());
                    match = match && picked.stream().anyMatch(p -> p.primaryType == t || p.secondaryType == t);
                }
                if (match) {
                    StringBuilder names = new StringBuilder();
                    for (com.dabomstew.pkrandom.pokemon.Pokemon p : picked) {
                        names.append(p.number).append(' ');
                    }
                    System.out.println("probe seed=" + s + " starters=" + names.toString().trim());
                }
            }
            return 0;
        }
        if ((settingsPath == null && settingsString == null) || source == null || destination == null) {
            System.err.println("ERROR: -s, -i and -o are required");
            usage();
            return 2;
        }

        Path patched = null;
        try {
            Settings settings;
            if (settingsString != null) {
                settings = Settings.fromString(settingsString);
            } else {
                try (FileInputStream fis = new FileInputStream(settingsPath)) {
                    settings = Settings.read(fis);
                }
            }
            settings.setCustomNames(FileFunctions.getCustomNames());
            if (guaranteedCatch) {
                settings.setUseMinimumCatchRate(true);
                settings.setMinimumCatchRateLevel(5);
            }

            String loadPath = source;
            if (baseIps != null) {
                byte[] src = Files.readAllBytes(Paths.get(source));
                byte[] patch = Files.readAllBytes(Paths.get(baseIps));
                byte[] out = IpsPatcher.apply(src, patch);
                patched = Files.createTempFile("ddoz-base-", ".gba");
                Files.write(patched, out);
                loadPath = patched.toString();
            }

            if (seed >= 0) {
                RandomSource.seed(seed);
            }

            RomHandler.Factory[] handlers = new RomHandler.Factory[] {
                    new Gen1RomHandler.Factory(), new Gen2RomHandler.Factory(), new Gen3RomHandler.Factory(),
                    new Gen4RomHandler.Factory(), new Gen5RomHandler.Factory(), new Gen6RomHandler.Factory(),
                    new Gen7RomHandler.Factory()
            };

            RomHandler handler = null;
            RomHandler.Factory chosenFactory = null;
            for (RomHandler.Factory factory : handlers) {
                if (factory.isLoadable(loadPath)) {
                    chosenFactory = factory;
                    handler = factory.create(RandomSource.instance());
                    handler.loadRom(loadPath);
                    break;
                }
            }
            if (handler == null) {
                System.err.println("ERROR: unsupported ROM");
                return 1;
            }

            if (weights) {
                com.dabomstew.pkrandom.ddoz.SpeciesWeights.configure(handler, weightPower, weightCap, biases, biasScope);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PrintStream log = new PrintStream(buffer, false, "UTF-8");
            Randomizer randomizer = new Randomizer(settings, handler, java.util.ResourceBundle
                    .getBundle("com/dabomstew/pkrandom/newgui/Bundle"), false);
            long effectiveSeed = seed >= 0 ? seed : RandomSource.pickSeed();
            randomizer.randomize(destination, log, effectiveSeed);
            log.close();

            if (saveLog) {
                try (FileOutputStream fos = new FileOutputStream(destination + ".log")) {
                    fos.write(0xEF);
                    fos.write(0xBB);
                    fos.write(0xBF);
                    fos.write(buffer.toByteArray());
                }
            }

            if (fingerprints) {
                printFingerprints(handler, destination);
            }
            if (doubleCheck) {
                Path check = Paths.get(destination + ".check");
                try (PrintStream checkLog = new PrintStream(new ByteArrayOutputStream())) {
                    RomHandler second = chosenFactory.create(RandomSource.instance());
                    second.loadRom(loadPath);
                    Randomizer redo = new Randomizer(settings, second, java.util.ResourceBundle
                            .getBundle("com/dabomstew/pkrandom/newgui/Bundle"), false);
                    redo.randomize(check.toString(), checkLog, effectiveSeed);
                    boolean same = sha1(Paths.get(destination)).equals(sha1(check));
                    System.out.println("double-check " + (same ? "ok" : "MISMATCH"));
                    Files.deleteIfExists(check);
                    Files.deleteIfExists(Paths.get(check.toString() + ".log"));
                    if (!same) {
                        return 1;
                    }
                }
            }
            if (printSha1) {
                System.out.println("sha1 " + sha1(Paths.get(destination)));
            }
            System.out.println("Randomized successfully!");
            return 0;
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            return 1;
        } finally {
            if (patched != null) {
                try { Files.deleteIfExists(patched); } catch (IOException ignored) { }
            }
        }
    }

    private static Settings loadSettings(String path, String string, String presetName) throws Exception {
        if (string != null) {
            return Settings.fromString(string);
        }
        if (path != null) {
            try (FileInputStream fis = new FileInputStream(path)) {
                return Settings.read(fis);
            }
        }
        try (InputStream in = DdozCli.class.getResourceAsStream("kaizo-v1.rnqs")) {
            if (in == null) {
                throw new IOException("preset resource missing");
            }
            Path temp = Files.createTempFile("ddoz-preset-", ".rnqs");
            try {
                Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                try (FileInputStream fis = new FileInputStream(temp.toFile())) {
                    return Settings.read(fis);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }
    private static int printPreset(String name) {
        if (!"kaizo-v1".equals(name)) {
            System.err.println("ERROR: unknown preset " + name);
            return 2;
        }
        try (InputStream in = DdozCli.class.getResourceAsStream("kaizo-v1.rnqs")) {
            if (in == null) {
                System.err.println("ERROR: preset resource missing");
                return 1;
            }
            Path temp = Files.createTempFile("ddoz-preset-", ".rnqs");
            try {
                Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                try (FileInputStream fis = new FileInputStream(temp.toFile())) {
                    Settings settings = Settings.read(fis);
                    System.out.println(settings.toString());
                }
            } finally {
                Files.deleteIfExists(temp);
            }
            return 0;
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            return 1;
        }
    }

    private static String next(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value after " + args[index - 1]);
        }
        return args[index];
    }

    private static void printFingerprints(RomHandler handler, String romPath) throws Exception {
        StringBuilder starters = new StringBuilder();
        for (com.dabomstew.pkrandom.pokemon.Pokemon p : handler.getStarters()) {
            starters.append(p.number).append(';');
        }
        System.out.println("fingerprint:starters " + sha256(starters.toString()));

        StringBuilder wild = new StringBuilder();
        for (com.dabomstew.pkrandom.pokemon.EncounterSet set : handler.getEncounters(false)) {
            for (com.dabomstew.pkrandom.pokemon.Encounter enc : set.encounters) {
                wild.append(enc.pokemon.number).append(':').append(enc.level).append(';');
            }
        }
        System.out.println("fingerprint:wild " + sha256(wild.toString()));

        StringBuilder trainers = new StringBuilder();
        for (com.dabomstew.pkrandom.pokemon.Trainer trainer : handler.getTrainers()) {
            for (com.dabomstew.pkrandom.pokemon.TrainerPokemon tp : trainer.pokemon) {
                trainers.append(tp.pokemon.number).append(':').append(tp.level).append(';');
            }
        }
        System.out.println("fingerprint:trainers " + sha256(trainers.toString()));

        StringBuilder moves = new StringBuilder();
        for (Map.Entry<Integer, List<com.dabomstew.pkrandom.pokemon.MoveLearnt>> e
                : handler.getMovesLearnt().entrySet()) {
            moves.append(e.getKey()).append('=');
            for (com.dabomstew.pkrandom.pokemon.MoveLearnt ml : e.getValue()) {
                moves.append(ml.level).append(':').append(ml.move).append(',');
            }
            moves.append(';');
        }
        System.out.println("fingerprint:movesets " + sha256(moves.toString()));
        System.out.println("fingerprint:rom " + sha1(Paths.get(romPath)));
    }

    private static String sha256(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sha1(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(path));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void usage() {
        System.err.println("Usage: java -jar ddoz-zx.jar cli -s <settings> -i <rom> -o <out> " +
                "[-z <seed>] [--base-ips <ips>] [--guaranteed-catch] [-l] [--print-sha1] " +
                "[--print-preset kaizo-v1]");
    }
}
