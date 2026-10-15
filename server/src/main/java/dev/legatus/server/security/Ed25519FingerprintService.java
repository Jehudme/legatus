package dev.legatus.server.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Service responsible for computing Ed25519 public key cryptographic fingerprints
 * and deterministic 3-word visual confirmation codes for worker node attestation.
 * <p>
 * Complies with {@code ADR-0005} and {@code server-spec.md} Section 4.1:
 * <ul>
 *   <li>Computes SHA-256 over raw 32-byte Ed25519 public key bytes.</li>
 *   <li>Formats the first 8 bytes of the digest as an uppercase colon-separated hex string
 *       ({@code XX:XX:XX:XX:XX:XX:XX:XX}).</li>
 *   <li>Derives a deterministic visual code ({@code Word1-Word2-Number}, e.g. {@code Tiger-Galaxy-77})
 *       from lower bytes of the digest for visual cockpit attestation.</li>
 * </ul>
 */
@Service
public class Ed25519FingerprintService {

    public static final int ED25519_PUBLIC_KEY_LEN = 32;

    private static final List<String> WORD_LIST_1 = List.of(
        "Aardvark", "Albatross", "Alligator", "Alpaca", "Antelope", "Armadillo", "Badger", "Barracuda",
        "Bat", "Bear", "Beaver", "Bison", "Boar", "Buffalo", "Camel", "Capybara",
        "Caribou", "Cassowary", "Cat", "Caterpillar", "Chameleon", "Cheetah", "Chimpanzee", "Chinchilla",
        "Cobra", "Condor", "Cormorant", "Coyote", "Crab", "Crane", "Crocodile", "Crow",
        "Deer", "Dingo", "Dolphin", "Donkey", "Dove", "Dragonfly", "Duck", "Eagle",
        "Echidna", "Elephant", "Elk", "Emu", "Falcon", "Ferret", "Finch", "Flamingo",
        "Fox", "Frog", "Gazelle", "Gecko", "Gerbil", "Giraffe", "Gnu", "Goat",
        "Goose", "Gorilla", "Goshawk", "Grasshopper", "Gull", "Hamster", "Hare", "Hawk",
        "Hedgehog", "Heron", "Herring", "Hippopotamus", "Hornet", "Horse", "Hummingbird", "Hyena",
        "Ibex", "Ibis", "Iguana", "Impala", "Jackal", "Jaguar", "Jay", "Jellyfish",
        "Kangaroo", "Kestrel", "Kingfisher", "Koala", "Kookaburra", "Kudu", "Lapwing", "Lark",
        "Lemur", "Leopard", "Lion", "Llama", "Lobster", "Loris", "Lynx", "Lyrebird",
        "Magpie", "Mallard", "Mammoth", "Manatee", "Mandrill", "Mantis", "Marmot", "Meerkat",
        "Mink", "Mole", "Mongoose", "Monkey", "Moose", "Mosquito", "Mouse", "Narwhal",
        "Newt", "Nightingale", "Octopus", "Okapi", "Opossum", "Oryx", "Osprey", "Ostrich",
        "Otter", "Owl", "Ox", "Oyster", "Panther", "Parrot", "Partridge", "Peacock",
        "Pelican", "Penguin", "Pheasant", "Pig", "Pigeon", "Pika", "Platypus", "Plover",
        "Polarbear", "Pony", "Porcupine", "Porpoise", "Quail", "Quelea", "Quokka", "Rabbit",
        "Raccoon", "Ram", "Rat", "Raven", "Reddeer", "Redpanda", "Reindeer", "Rhinoceros",
        "Rook", "Salamander", "Salmon", "Sandpiper", "Sardine", "Scorpion", "Seahorse", "Seal",
        "Shark", "Sheep", "Shrew", "Skunk", "Sloth", "Snail", "Snake", "Sparrow",
        "Spider", "Spoonbill", "Squid", "Squirrel", "Starling", "Stingray", "Stork", "Swan",
        "Tapir", "Tarsier", "Termite", "Tiger", "Toad", "Toucan", "Trout", "Turkey",
        "Turtle", "Viper", "Vole", "Vulture", "Wallaby", "Walrus", "Wasp", "Weasel",
        "Whale", "Wildcat", "Wolf", "Wolverine", "Wombat", "Woodcock", "Woodpecker", "Worm",
        "Wren", "Yak", "Zebra", "Axolotl", "Bandicoot", "Binturong", "Bobcat", "Bongo",
        "Bonobo", "Capuchin", "Caracal", "Coati", "Colobus", "Cuscus", "Dasyure", "Dikdik",
        "Drill", "Dugong", "Eland", "Falanouc", "Fennec", "Fisher", "Galago", "Gaur",
        "Genet", "Goral", "Grison", "Guanaco", "Hartebeest", "Huemul", "Hutia", "Hyrax",
        "Indri", "Jaguarundi", "Kakapo", "Kinkajou", "Klipspringer", "Kodkod", "Kori", "Kowari",
        "Langur", "Lechwe", "Margay", "Markhor", "Marmoset", "Marten", "Monito", "Muntjac",
        "Muskox", "Muskrat", "Nandinia", "Nilgai", "Numbat", "Ocelot", "Onager", "Oribi",
        "Paca", "Pademelon", "Pangolin", "Peccary", "Potoroo", "Pudu", "Puggle", "Quoll",
        "Ratel", "Saki", "Serval", "Sifaka", "Sitatunga", "Tamarin", "Takin", "Tayra"
    );

    private static final List<String> WORD_LIST_2 = List.of(
        "Andromeda", "Antares", "Aphelion", "Apollo", "Archipelago", "Asteroid", "Astron", "Atmosphere",
        "Aurora", "Australis", "Azimuth", "Barchan", "Basalt", "Bermuda", "Binary", "Biolume",
        "Blackhole", "Blizzard", "Borealis", "Caldera", "Canyon", "Cascade", "Celestial", "Centauri",
        "Chasm", "Chromosphere", "Cirrus", "Cluster", "Colossus", "Comet", "Constellation", "Corona",
        "Cosmic", "Cosmos", "Crater", "Crescent", "Cumulus", "Cyclone", "Dawn", "Delta",
        "Draco", "Dune", "Eclipse", "Ecliptic", "Electra", "Equinox", "Exosphere", "Fjord",
        "Flare", "Frontier", "Galactic", "Galaxy", "Gamma", "Geode", "Geyser", "Glacier",
        "Gorge", "Gravity", "Halo", "Helios", "Horizon", "Hubble", "Hydra", "Hyperion",
        "Iceberg", "Infinity", "Infrared", "Ionosphere", "Islet", "Jupiter", "Kepler", "Lagoon",
        "Latitude", "Lightyear", "Lithosphere", "Longitude", "Luminance", "Lunar", "Magnetar", "Mantle",
        "Mars", "Matrix", "Megavolt", "Mercury", "Mesa", "Meteor", "Meteorite", "Milkyway",
        "Mirage", "Monolith", "Monsoon", "Moon", "Moonlight", "Moraine", "Nebula", "Neutron",
        "Nimbus", "Nova", "Oasis", "Observatory", "Ocean", "Olympus", "Omega", "Oort",
        "Orbit", "Orbital", "Orion", "Outback", "Parallax", "Pegasus", "Penumbra", "Perihelion",
        "Permafrost", "Photon", "Plateau", "Pleiades", "Polaris", "Prairie", "Prominence", "Protostar",
        "Pulsar", "Quantum", "Quasar", "Radiation", "Radiance", "Ravine", "Redshift", "Reef",
        "Relic", "Resonance", "Rift", "Riptide", "Satellite", "Saturn", "Savanna", "Scarp",
        "Singularity", "Sirocco", "Sol", "Solar", "Solstice", "Spectra", "Spectrum", "Spiral",
        "Starlight", "Stellar", "Stratosphere", "Subduction", "Sunburst", "Supercluster", "Supernova", "Syncline",
        "Taiga", "Tectonic", "Telescope", "Tesseract", "Tharsis", "Thermal", "Tidal", "Titan",
        "Topography", "Tornado", "Trajectory", "Transition", "Troposphere", "Tsunami", "Tundra", "Twilight",
        "Typhoon", "Ultraviolet", "Umbra", "Universe", "Uranus", "Vacuum", "Valhalla", "Valles",
        "Vega", "Velocity", "Venus", "Vertex", "Vesper", "Volcano", "Vortex", "Voyager",
        "Wavelength", "Whitehole", "Wilderness", "Windshear", "Wormhole", "Zenith", "Zephyr", "Zodiac",
        "Abyss", "Aeon", "Apex", "Arcadia", "Aura", "Beacon", "Cataract", "Chrono",
        "Citadel", "Cliff", "Conduit", "Cosmodrome", "Crest", "Crown", "Current", "Dynamo",
        "Element", "Empyrean", "Epoch", "Eon", "Estuary", "Ether", "Firmament", "Flash",
        "Flux", "Genesis", "Gradient", "Haven", "Helix", "Highland", "Illusion", "Impulse",
        "Infinity", "Ion", "Labyrinth", "Levant", "Lode", "Loom", "Luminescence", "Magma",
        "Miracle", "Mirage", "Nexus", "Nucleus", "Omen", "Origin", "Palisade", "Passage",
        "Peak", "Pinnacle", "Plasma", "Polar", "Prism", "Pyramid", "Radiant", "Ray",
        "Ridge", "Runoff", "Sanctuary", "Sanctum", "Shadow", "Shoal", "Signet", "Solarium",
        "Spire", "Strata", "Summit", "Tempest", "Thermal", "Tide", "Torrent", "Vector"
    );

    /**
     * Immutable result holding the derived fingerprint artifacts.
     */
    public record FingerprintResult(
        String fingerprintHex,
        String visualCode,
        byte[] sha256Digest
    ) {}

    /**
     * Computes the Ed25519 fingerprint and visual confirmation code from raw 32-byte public key.
     *
     * @param publicKeyBytes 32-byte raw Ed25519 public key
     * @return the derived {@link FingerprintResult}
     * @throws IllegalArgumentException if public key is null or not 32 bytes
     */
    public FingerprintResult derive(byte[] publicKeyBytes) {
        Objects.requireNonNull(publicKeyBytes, "publicKeyBytes cannot be null");
        if (publicKeyBytes.length != ED25519_PUBLIC_KEY_LEN) {
            throw new IllegalArgumentException(
                "Ed25519 public key must be exactly 32 bytes, got " + publicKeyBytes.length
            );
        }

        byte[] digest = computeSha256(publicKeyBytes);
        String fingerprintHex = formatFingerprintHex(digest);
        String visualCode = deriveVisualCode(digest);

        return new FingerprintResult(fingerprintHex, visualCode, digest);
    }

    /**
     * Derives fingerprint from Base64-encoded Ed25519 public key.
     *
     * @param base64PublicKey RFC 4648 standard base64 string
     * @return the derived {@link FingerprintResult}
     */
    public FingerprintResult deriveFromBase64(String base64PublicKey) {
        Objects.requireNonNull(base64PublicKey, "base64PublicKey cannot be null");
        byte[] raw = java.util.Base64.getDecoder().decode(base64PublicKey.trim());
        return derive(raw);
    }

    /**
     * Derives fingerprint from hex-encoded Ed25519 public key.
     *
     * @param hexPublicKey 64-character hex string
     * @return the derived {@link FingerprintResult}
     */
    public FingerprintResult deriveFromHex(String hexPublicKey) {
        Objects.requireNonNull(hexPublicKey, "hexPublicKey cannot be null");
        String clean = hexPublicKey.trim().replaceAll(":", "").replaceAll("-", "");
        byte[] raw = HexFormat.of().parseHex(clean);
        return derive(raw);
    }

    /**
     * Formats the first 8 bytes of the SHA-256 digest as colon-separated uppercase hex.
     * E.g., {@code 4E:8A:BC:31:09:FF:7D:12}
     */
    private String formatFingerprintHex(byte[] digest) {
        StringBuilder sb = new StringBuilder(23); // 8 * 2 hex + 7 colons
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02X", digest[i]));
        }
        return sb.toString();
    }

    /**
     * Derives the deterministic 3-word visual code (Word1-Word2-Number) from lower bytes of digest.
     * Bytes 8, 9, 10 of the SHA-256 digest are mapped to dictionary words and 2-digit number.
     */
    private String deriveVisualCode(byte[] digest) {
        int idx1 = (digest[8] & 0xFF) % WORD_LIST_1.size();
        int idx2 = (digest[9] & 0xFF) % WORD_LIST_2.size();
        int num = Math.abs((digest[10] & 0xFF) % 100);

        String word1 = WORD_LIST_1.get(idx1);
        String word2 = WORD_LIST_2.get(idx2);
        return String.format("%s-%s-%02d", word1, word2, num);
    }

    private byte[] computeSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
