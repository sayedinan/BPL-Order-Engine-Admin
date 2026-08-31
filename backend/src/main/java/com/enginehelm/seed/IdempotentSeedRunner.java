package com.enginehelm.seed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.enginehelm.accessgroup.AccessGroup;
import com.enginehelm.accessgroup.AccessGroupEngine;
import com.enginehelm.accessgroup.AccessGroupEngineRepository;
import com.enginehelm.accessgroup.AccessGroupRepository;
import com.enginehelm.accessgroup.UserAccessGroup;
import com.enginehelm.accessgroup.UserAccessGroupRepository;
import com.enginehelm.credential.Credential;
import com.enginehelm.credential.CredentialRepository;
import com.enginehelm.credential.CredentialType;
import com.enginehelm.engine.Engine;
import com.enginehelm.engine.EngineRepository;
import com.enginehelm.host.Host;
import com.enginehelm.host.HostRepository;
import com.enginehelm.keystore.KeystoreCredentialStore;
import com.enginehelm.user.SystemRole;
import com.enginehelm.user.User;
import com.enginehelm.user.UserRepository;

/**
 * Idempotent seed runner. Per SPEC §9, the seed is guarded by a
 * per-table count check, so re-running on a populated DB is a
 * no-op. H2 does not accept {@code INSERT ... WHERE NOT EXISTS}
 * directly, so this shape is the one that works.
 *
 * <p>Generates an SSH keypair per credential at first boot, encrypts
 * the private key via {@code KeystoreCredentialStore}, and prints the
 * public keys to stdout so the operator can paste them into the
 * target hosts' {@code authorized_keys}.
 */
@Component
@Order(0)
public class IdempotentSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IdempotentSeedRunner.class);

    /** Demo password (BCrypt-hashed). Per SPEC §10.1, the password is set by sys.admin directly in V1. */
    private static final String DEMO_PASSWORD = "devpassword";

    @PersistenceContext
    private EntityManager em;

    private final UserRepository users;
    private final HostRepository hosts;
    private final CredentialRepository credentials;
    private final AccessGroupRepository accessGroups;
    private final UserAccessGroupRepository userAccessGroups;
    private final AccessGroupEngineRepository accessGroupEngines;
    private final KeystoreCredentialStore keystore;
    private final EngineRepository engines;
    private final PasswordEncoder passwordEncoder;

    /** Cached during seedAccessGroups() for the engine-join step. */
    private Long seededWebGroupId;
    private Long seededWorkersGroupId;

    public IdempotentSeedRunner(UserRepository users,
                                HostRepository hosts,
                                CredentialRepository credentials,
                                AccessGroupRepository accessGroups,
                                UserAccessGroupRepository userAccessGroups,
                                AccessGroupEngineRepository accessGroupEngines,
                                KeystoreCredentialStore keystore,
                                EngineRepository engines,
                                PasswordEncoder passwordEncoder) {
        this.users = users;
        this.hosts = hosts;
        this.credentials = credentials;
        this.accessGroups = accessGroups;
        this.userAccessGroups = userAccessGroups;
        this.accessGroupEngines = accessGroupEngines;
        this.keystore = keystore;
        this.engines = engines;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() == 0) seedUsers();
        if (credentials.count() == 0) seedCredentialsAndHosts();
        if (accessGroups.count() == 0) seedAccessGroups();
        if (engines.count() == 0) seedEngines();
        log.info("Seed run complete. users={}, hosts={}, credentials={}, engines={}, accessGroups={}",
                users.count(), hosts.count(), credentials.count(), engines.count(),
                accessGroups.count());
    }

    private void seedUsers() {
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        saveUser("sysadmin@local", hash, SystemRole.SYS_ADMIN);
        saveUser("admin@local", hash, SystemRole.ADMIN);
        saveUser("alice@local", hash, SystemRole.STANDARD);
        saveUser("bob@local", hash, SystemRole.STANDARD);
        log.info("Seeded 4 users (password = '{}' for all)", DEMO_PASSWORD);
    }

    private void saveUser(String username, String hash, SystemRole role) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(hash);
        u.setSystemRole(role);
        u.setMustChangePassword(false);
        u.setEnabled(true);
        users.save(u);
    }

    private void seedCredentialsAndHosts() {
        // Generate a keypair per demo host and store the public key
        // so the operator can paste it. Private keys are encrypted
        // via KeystoreCredentialStore before persisting.
        Map<String, Credential> creds = new LinkedHashMap<>();
        for (String alias : List.of("cred-web-tier", "cred-worker-tier", "cred-isolated")) {
            try {
                KeyPair kp = generateRsa();
                String publicOpenSsh = renderOpenSshPublic(kp.getPublic());
                String fingerprint = sha256Fingerprint(publicOpenSsh);
                Credential c = new Credential();
                c.setAlias(alias);
                c.setType(CredentialType.ssh_key);
                c.setFingerprint(fingerprint);
                c.setPrivateKeyCiphertext(keystore.encrypt(kp.getPrivate().getEncoded()));
                Credential saved = credentials.save(c);
                creds.put(alias, saved);
                log.info("=== SSH public key for {} ===\n{}\n=== paste into authorized_keys ===",
                        alias, publicOpenSsh);
            } catch (Exception e) {
                throw new IllegalStateException("failed to seed credential " + alias, e);
            }
        }

        // Hosts reference the credential via default_credential_id.
        // Host key fingerprints are placeholder SHA-256 hashes; real
        // pinning happens at first boot via the admin UI (out of
        // scope for this plan).
        saveHost("web-tier-host", "web-tier.example.invalid", 22, "engine",
                "SHA256:PLACEHOLDER_WEB_TIER", creds.get("cred-web-tier").getId());
        saveHost("worker-tier-host", "worker-tier.example.invalid", 22, "engine",
                "SHA256:PLACEHOLDER_WORKER_TIER", creds.get("cred-worker-tier").getId());
        saveHost("isolated-host", "isolated.example.invalid", 22, "engine",
                "SHA256:PLACEHOLDER_ISOLATED", creds.get("cred-isolated").getId());
    }

    private void saveHost(String alias, String host, int port, String sshUser,
                          String fingerprint, Long defaultCredentialId) {
        Host h = new Host();
        h.setAlias(alias);
        h.setHostnameOrIp(host);
        h.setPort(port);
        h.setSshUsername(sshUser);
        h.setHostKeyFingerprint(fingerprint);
        h.setDefaultCredentialId(defaultCredentialId);
        hosts.save(h);
    }

    private void seedAccessGroups() {
        Long sysadminId = users.findByUsername("sysadmin@local").orElseThrow().getId();
        Long aliceId = users.findByUsername("alice@local").orElseThrow().getId();
        Long bobId = users.findByUsername("bob@local").orElseThrow().getId();

        AccessGroup web = new AccessGroup();
        web.setName("Engines — Web Tier");
        web.setDescription("Demo: web tier engines");
        web.setCreatedBy(sysadminId);
        accessGroups.save(web);

        AccessGroup workers = new AccessGroup();
        workers.setName("Engines — Workers");
        workers.setDescription("Demo: worker tier engines");
        workers.setCreatedBy(sysadminId);
        accessGroups.save(workers);

        // alice: web tier only
        addUserToGroup(aliceId, web.getId());
        // bob: both groups (the union-case demo)
        addUserToGroup(bobId, web.getId());
        addUserToGroup(bobId, workers.getId());

        // Cache group ids for the engine-join step.
        seededWebGroupId = web.getId();
        seededWorkersGroupId = workers.getId();
    }

    private void addUserToGroup(Long userId, Long groupId) {
        UserAccessGroup uag = new UserAccessGroup();
        uag.setUserId(userId);
        uag.setGroupId(groupId);
        userAccessGroups.save(uag);
    }

    private void seedEngines() {
        Long webHost = hosts.findAll().stream()
                .filter(h -> "web-tier-host".equals(h.getAlias())).findFirst().orElseThrow().getId();
        Long workerHost = hosts.findAll().stream()
                .filter(h -> "worker-tier-host".equals(h.getAlias())).findFirst().orElseThrow().getId();
        Long isoHost = hosts.findAll().stream()
                .filter(h -> "isolated-host".equals(h.getAlias())).findFirst().orElseThrow().getId();

        Long e1 = saveEngine("eng-web-01", webHost,
                "systemctl restart myapp", "systemctl stop myapp",
                "systemctl is-active myapp", "tail -n 200 /var/log/myapp.log");
        Long e2 = saveEngine("eng-web-02", webHost,
                "systemctl restart myapp2", "systemctl stop myapp2",
                "systemctl is-active myapp2", "tail -n 200 /var/log/myapp2.log");
        Long e3 = saveEngine("eng-worker-01", workerHost,
                "systemctl restart worker", "systemctl stop worker",
                "systemctl is-active worker", "tail -n 200 /var/log/worker.log");
        Long e4 = saveEngine("eng-worker-02", workerHost,
                "systemctl restart worker2", "systemctl stop worker2",
                "systemctl is-active worker2", "tail -n 200 /var/log/worker2.log");
        saveEngine("eng-isolated", isoHost,
                "systemctl restart isolated", "systemctl stop isolated",
                "systemctl is-active isolated", "tail -n 200 /var/log/isolated.log");

        // Web Tier group: e1, e2
        addEngineToGroup(seededWebGroupId, e1);
        addEngineToGroup(seededWebGroupId, e2);
        // Workers group: e2 (overlap), e3, e4
        addEngineToGroup(seededWorkersGroupId, e2);
        addEngineToGroup(seededWorkersGroupId, e3);
        addEngineToGroup(seededWorkersGroupId, e4);
    }

    private void addEngineToGroup(Long groupId, Long engineId) {
        AccessGroupEngine age = new AccessGroupEngine();
        age.setGroupId(groupId);
        age.setEngineId(engineId);
        accessGroupEngines.save(age);
    }

    private Long saveEngine(String name, Long hostId, String start, String stop, String status, String log) {
        Engine e = new Engine();
        e.setName(name);
        e.setHostId(hostId);
        e.setStartScript(start);
        e.setStopScript(stop);
        e.setStatusScript(status);
        e.setLogScript(log);
        return engines.save(e).getId();
    }

    private static KeyPair generateRsa() throws NoSuchAlgorithmException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private static String renderOpenSshPublic(PublicKey pub) throws IOException {
        // OpenSSH wire format: "ssh-rsa <base64> <comment>".
        // Build the wire blob: <string "ssh-rsa"> <mpint e> <mpint n>.
        RSAPublicKey rsa = (RSAPublicKey) pub;
        byte[] alg = "ssh-rsa".getBytes(StandardCharsets.US_ASCII);
        byte[] blob = concat(
                encodeString(alg),
                encodeMpint(rsa.getPublicExponent().toByteArray()),
                encodeMpint(rsa.getModulus().toByteArray()));
        String b64 = Base64.getEncoder().encodeToString(blob);
        return "ssh-rsa " + b64 + " engine-helm@local";
    }

    private static byte[] encodeString(byte[] s) {
        byte[] len = uInt32(s.length);
        return concat(len, s);
    }

    private static byte[] encodeMpint(byte[] n) {
        // Strip leading zero (if any) and prepend zero if high bit set.
        int start = 0;
        while (start < n.length - 1 && n[start] == 0) start++;
        if ((n[start] & 0x80) != 0) {
            byte[] padded = new byte[n.length - start + 1];
            System.arraycopy(n, start, padded, 1, n.length - start);
            return concat(uInt32(padded.length), padded);
        }
        byte[] trimmed = new byte[n.length - start];
        System.arraycopy(n, start, trimmed, 0, trimmed.length);
        return concat(uInt32(trimmed.length), trimmed);
    }

    private static byte[] uInt32(int n) {
        return new byte[]{
                (byte) ((n >>> 24) & 0xff),
                (byte) ((n >>> 16) & 0xff),
                (byte) ((n >>> 8) & 0xff),
                (byte) (n & 0xff)
        };
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b, byte[] c) {
        return concat(a, concat(b, c));
    }

    private static String sha256Fingerprint(String openSsh) throws NoSuchAlgorithmException {
        // Use the bytes that would go in authorized_keys (after the
        // algorithm + comment) for a displayable fingerprint.
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(openSsh.getBytes(StandardCharsets.UTF_8));
            return "SHA256:" + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw e;
        }
    }
}
