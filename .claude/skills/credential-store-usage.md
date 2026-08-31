---
name: credential-store-usage
description: |
  How to read / write a credential via the Engine Helm JCE keystore,
  how to generate a fresh keypair via the admin UI without exposing
  the private key outside the keystore, and the explicit prohibition
  on ever reading or printing ENGINE_HELM_MASTER_KEY.
---

# credential-store-usage

Engine Helm stores SSH private keys in a Java JCE keystore. The
master key is supplied via the `ENGINE_HELM_MASTER_KEY` environment
variable at deploy time. This skill is the canonical reference for
working with the credential store correctly.

## Hard rules

These are non-negotiable. The security-reviewer will fail any PR
that violates them.

1. **Never read `ENGINE_HELM_MASTER_KEY` into a string, log it,
   return it from an API, or write it to a file outside the
   keystore.** It is a secret. Treat it like a database root
   password.
2. **Never commit `ENGINE_HELM_MASTER_KEY` to git.** Not in a file.
   Not in a commit message. Not in a PR description. Not in chat.
   Not in project Context. If it ever leaks, rotate immediately
   (§ "Master-key rotation" below).
3. **Never put `ENGINE_HELM_MASTER_KEY` in a tracked `.env` file.**
   The repo's `.claude/settings.json` denies reads of `./.env` and
   `./.env.*` for this reason.
4. **Never log decrypted private key bytes.** The SSH service
   decrypts a key into a local `byte[]` and zeroes it in a
   `finally`. Do not print, log, or otherwise surface those bytes.
5. **Never persist decrypted private key bytes to disk.** They live
   in the process memory only, for the duration of one SSH call.

## Boot-time precondition

`ENGINE_HELM_MASTER_KEY` must be set in the JVM environment when
the app starts. The application **fails to start** if it is unset —
there is no silent fallback to a default or empty key.

This is enforced at boot by a startup hook. The error message is
explicit and non-leaking (it does not echo the env var's value).

## Reading a credential

`KeystoreCredentialStore` is the only public interface for reading
credentials. The SSH service uses it like:

```java
public interface KeystoreCredentialStore {
    /**
     * Returns the decrypted private key bytes for a credential.
     * Callers MUST zero the returned array in a `finally` block.
     */
    byte[] loadPrivateKey(long credentialId);
}
```

The interface is synchronous, returns `byte[]` (not `String`), and
the SSH service is responsible for zeroing the array in a
`finally`. **Do not** cache the key across calls.

## Writing a credential

When a sys.admin creates a new credential via the admin UI:

1. The UI generates a fresh keypair on the server side (Ed25519
   preferred, RSA-4096 acceptable as a fallback).
2. The **private key** is encrypted with `ENGINE_HELM_MASTER_KEY`
   and written to the JCE keystore. The UI never returns the
   private key to the browser.
3. The **public key** is returned to the admin once, displayed
   in the UI as a copyable OpenSSH-formatted block. The admin
   pastes it into the target host's `authorized_keys`.
4. The credential's SHA-256 fingerprint is shown alongside the
   public key. The fingerprint is what other admins see in the
   list view; the public key is only shown at creation time.

This is the "generate a fresh keypair via the admin UI without
exposing the private key outside the keystore" path that
`SPEC.md §11` calls out as a future-proofing requirement.

## Pasting an existing private key (V1: not supported)

V1 does not support pasting an existing private key into the admin
UI. Adding this is a deliberate omission: paste-in paths are a
common credential-leak surface (browser autofill, clipboard
managers, screen recordings). V1 only supports server-side
keypair generation. If a host requires a specific existing key,
the operator should run a one-off `ssh-keygen` outside the app and
manually inject the public key into the host's `authorized_keys`,
then create a new keypair in Engine Helm and re-point the host.

This is a V1 scope decision and not a permanent restriction. A
paste-in path with strict controls (paste is not autofilled, is
cleared from the form on submit, etc.) is a known follow-up.

## Master-key rotation

There is **no** master-key rotation UI in V1. Rotation is
out-of-band:

1. Generate a new master key out-of-band.
2. Deploy the new `ENGINE_HELM_MASTER_KEY` to the running
   environment.
3. Run a re-encrypt helper (TBD; not in V1) that decrypts every
   credential entry with the old key and re-encrypts with the
   new one.
4. Restart the application with only the new key in the env.
5. Confirm via the admin UI that every credential still resolves.

If `ENGINE_HELM_MASTER_KEY` ever leaks, do this immediately and
assume every credential in the store is compromised. Rotate the
target host `authorized_keys` for every engine, then re-create
the credential entries through the admin UI (which generates
fresh keypairs server-side).

## Audit trail

Every credential read is not logged (logging the read of a secret
is itself a leak surface). Every credential **add** and
**delete** is logged to `audit_log` with `category = CONFIG`,
actor identity, and a SHA-256 fingerprint reference. Audit
entries do not include the private key bytes or the master key.
