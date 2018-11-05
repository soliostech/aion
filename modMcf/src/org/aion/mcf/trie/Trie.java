/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 */
package org.aion.mcf.trie;

import java.util.Map;
import java.util.Set;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.util.ByteArrayWrapper;

/**
 * Trie interface for the main data structure in Ethereum which is used to store both the account
 * state and storage of each account.
 */
public interface Trie {

    /**
     * Gets a value from the trie for a given key
     *
     * @param key - any length byte array
     * @return an rlp encoded byte array of the stored object
     */
    byte[] get(byte[] key);

    /**
     * Insert or update a value in the trie for a specified key
     *
     * @param key - any length byte array
     * @param value rlp encoded byte array of the object to store
     */
    void update(byte[] key, byte[] value);

    /**
     * Deletes a key/value from the trie for a given key
     *
     * @param key - any length byte array
     */
    void delete(byte[] key);

    /**
     * Returns a SHA-3 hash from the top node of the trie
     *
     * @return 32-byte SHA-3 hash representing the entire contents of the trie.
     */
    byte[] getRootHash();

    /**
     * Set the top node of the trie
     *
     * @param root - 32-byte SHA-3 hash of the root node
     */
    void setRoot(byte[] root);

    /**
     * Used to check for corruption in the database.
     *
     * @param root a world state trie root
     * @return {@code true} if the root is valid, {@code false} otherwise
     */
    boolean isValidRoot(byte[] root);

    /** Commit all the changes until now */
    void sync();

    void sync(boolean flushCache);

    /** Discard all the changes until now */
    @Deprecated
    void undo();

    String getTrieDump();

    String getTrieDump(byte[] stateRoot);

    int getTrieSize(byte[] stateRoot);

    boolean validate();

    /**
     * Traverse the trie starting from the given node. Return the keys for all the missing branches
     * that are encountered during the traversal.
     *
     * @param key the starting node for the trie traversal
     * @return a set of keys that were referenced as part of the trie but could not be found in the
     *     database
     */
    Set<ByteArrayWrapper> getMissingNodes(byte[] key);

    /**
     * Retrieves nodes referenced by a trie node value, where the size of the result is bounded by
     * the given limit.
     *
     * @param value a trie node value which may be referencing other nodes
     * @param limit the maximum number of key-value pairs to be retrieved by this method, which
     *     limits the search in the trie; zero and negative values for the limit will result in no
     *     search and an empty map will be returned
     * @return an empty map when the value does not reference other trie nodes or the given limit is
     *     invalid, or a map containing all the referenced nodes reached while keeping within the
     *     limit on the result size
     */
    Map<ByteArrayWrapper, byte[]> getReferencedTrieNodes(byte[] value, int limit);

    long saveFullStateToDatabase(byte[] stateRoot, IByteArrayKeyValueDatabase db);

    long saveDiffStateToDatabase(byte[] stateRoot, IByteArrayKeyValueDatabase db);
}
