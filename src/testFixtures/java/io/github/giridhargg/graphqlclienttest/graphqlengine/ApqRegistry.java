package io.github.giridhargg.graphqlclienttest.graphqlengine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory store for the Automatic Persisted Query (APQ) two-phase
 * registration/lookup handshake.
 *
 * <p>APQ is a protocol popularised by Apollo that lets clients omit the full query string from most
 * requests, sending only a SHA-256 hash in {@code extensions.persistedQuery.sha256Hash} instead.
 * The server registers the hash-to-document mapping the first time it receives both (phase 2), then
 * serves subsequent hash-only requests directly from that mapping (phase 1).
 *
 * <h2>Lookup order</h2>
 *
 * <ol>
 *   <li>The static {@link GraphQlStaticTestAssets.Assets#persistedQueries()} map, loaded once at
 *       test startup from classpath files and never mutated.
 *   <li>This registry, populated dynamically at test runtime by phase-2 registration requests.
 * </ol>
 *
 * <h2>Lifecycle and isolation</h2>
 *
 * <p>One {@code ApqRegistry} instance is created per Spring {@code ApplicationContext} and shared
 * across all three transport paths (reactive in-memory, blocking in-memory, real-server
 * interceptor). It is cleared by {@link
 * io.github.giridhargg.graphqlclienttest.shared.MockGraphQlServerResetExtension} before each test
 * method alongside the {@code MockGraphQlServer} queue, so APQ state from one test cannot bleed
 * into the next.
 *
 * <h2>Phase 1 — hash-only lookup</h2>
 *
 * <p>Called when the incoming request carries {@code extensions.persistedQuery.sha256Hash} but no
 * {@code query} field (or an empty/null one). Returns {@link LookupResult} which is either a found
 * document or a {@link LookupResult.NotFound} signal that the caller must convert into a {@code
 * PersistedQueryNotFound} GraphQL error response and return to the client without executing the
 * query.
 *
 * <h2>Phase 2 — hash + query registration</h2>
 *
 * <p>Called when the incoming request carries both a {@code query} field and {@code
 * extensions.persistedQuery.sha256Hash}. The library validates that the hash matches the SHA-256 of
 * the query text before registering — an invalid hash produces a {@link
 * RegistrationResult.HashMismatch} which the caller should convert into a {@code
 * PersistedQueryHashMismatch} error response.
 */
public class ApqRegistry {

  private final Map<String, String> dynamicRegistry = new ConcurrentHashMap<>();

  /** Clears all dynamically registered entries. Called before each test method. */
  public void reset() {
    dynamicRegistry.clear();
  }

  /**
   * Looks up a document for the given hash, consulting first the static assets map then the dynamic
   * registry.
   *
   * @param hash the SHA-256 hex string from {@code extensions.persistedQuery.sha256Hash}
   * @param staticAssets the preloaded classpath assets (checked first)
   * @return {@link LookupResult.Found} with the document text, or {@link LookupResult.NotFound}
   */
  public LookupResult lookup(String hash, GraphQlStaticTestAssets.Assets staticAssets) {
    var fromStatic = staticAssets.persistedQueries().get(hash);
    if (fromStatic != null) return new LookupResult.Found(fromStatic);
    var fromDynamic = dynamicRegistry.get(hash);
    if (fromDynamic != null) return new LookupResult.Found(fromDynamic);
    return new LookupResult.NotFound(hash);
  }

  /**
   * Registers a {@code query} document under its {@code hash}, after validating that the hash
   * actually matches the SHA-256 of the query text.
   *
   * @param hash the SHA-256 hex string claimed by the client
   * @param query the full query document text
   * @return {@link RegistrationResult.Registered} on success, or {@link
   *     RegistrationResult.HashMismatch} if the hash does not match the query
   */
  public RegistrationResult register(String hash, String query) {
    var actualHash = sha256Hex(query);
    if (!actualHash.equalsIgnoreCase(hash)) {
      return new RegistrationResult.HashMismatch(hash, actualHash);
    }
    dynamicRegistry.put(hash, query);
    return new RegistrationResult.Registered(query);
  }

  // -------------------------------------------------------------------------
  // Result types
  // -------------------------------------------------------------------------

  /**
   * The result of a phase-1 APQ hash-only lookup.
   *
   * @see #lookup
   */
  public sealed interface LookupResult {
    /**
     * The hash was found; {@code query} is the resolved document text, ready for execution.
     *
     * @param query the full document text that was registered for this hash
     */
    record Found(String query) implements LookupResult {}

    /**
     * No document is registered for this hash — the caller must return a {@code
     * PersistedQueryNotFound} GraphQL error response to the client so it can retry with the full
     * query string (phase 2).
     *
     * @param hash the hash that was not found, for inclusion in the error response
     */
    record NotFound(String hash) implements LookupResult {}
  }

  /**
   * The result of a phase-2 APQ hash+query registration attempt.
   *
   * @see #register
   */
  public sealed interface RegistrationResult {
    /**
     * The hash was valid and the document has been registered. {@code query} is the document text,
     * ready for execution immediately (the client expects a real response, not a second round-trip
     * after registration).
     *
     * @param query the full document text that was just registered
     */
    record Registered(String query) implements RegistrationResult {}

    /**
     * The SHA-256 of the received query text does not match the claimed hash. This indicates a
     * client bug or tampering; the caller should return a {@code PersistedQueryHashMismatch} error
     * response.
     *
     * @param claimedHash the hash the client claimed
     * @param actualHash the hash actually computed from the query body
     */
    record HashMismatch(String claimedHash, String actualHash) implements RegistrationResult {}
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  private static String sha256Hex(String input) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
