/**
 * Assistant scope: the one model every service uses for rows that belong to an
 * assistant or are shared by all of an account's assistants (journeys,
 * templates, knowledge indexes; channels always belong to one).
 *
 * <ul>
 * <li>Stored as a nullable {@code assistant_id}; null means shared
 * ({@link com.itways.scope.AssistantScope}).</li>
 * <li>Writes go through {@link com.itways.scope.ScopeRules}: explicit
 * {@code shared}, else the named assistant, else the console's selected one,
 * else 400 {@code SCOPE_REQUIRED}. Another account's assistant is 404.</li>
 * <li>Lists take a {@code @RequestedScope} {@link com.itways.scope.ListScope}:
 * the {@code scope} query parameter, else the {@code X-Assistant-Id}
 * header (own plus shared), else the whole account. Until every caller has
 * moved, a request that sends only the legacy {@code X-Nibras-Assistant} is read
 * the same way ({@link com.itways.scope.LegacyAssistantHeaderFilter}).</li>
 * <li>Reads by id stay account-scoped, so links and pinned references keep
 * working when a row moves between assistants.</li>
 * <li>At run time the assistant's own row wins over a shared one with the same
 * name, and no assistant means shared rows only.</li>
 * </ul>
 */
package com.itways.scope;
