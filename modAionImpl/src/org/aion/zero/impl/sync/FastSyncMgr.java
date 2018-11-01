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
 * Contributors:
 *     Aion foundation.
 */

package org.aion.zero.impl.sync;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.aion.base.util.ByteArrayWrapper;

/**
 * Directs behavior for fast sync functionality.
 *
 * <p>Ensures that the full trie is eventually received by tracking the completeness of the trie
 * node imports.
 *
 * <p>Requests the trie nodes in reasonable batches.
 *
 * @author Alexandra Roatis
 */
public final class FastSyncMgr {

    private final Map<ByteArrayWrapper, byte[]> importedTrieNodes;

    public FastSyncMgr() {
        this.importedTrieNodes = new ConcurrentHashMap<>();
    }

    public void addImportedNode(ByteArrayWrapper key, byte[] value) {
        importedTrieNodes.put(key, value);
    }

    public boolean containsExact(ByteArrayWrapper key, byte[] value) {
        return importedTrieNodes.containsKey(key)
                && Arrays.equals(importedTrieNodes.get(key), value);
    }

    /** Changes the pivot in case of import failure. */
    public void handleFailedImport(
            ByteArrayWrapper key, byte[] value, TrieDatabase dbType, int peerId, String peer) {
        // TODO
    }

    /** @return {@code true} when fast sync is complete and secure */
    public boolean isComplete() {
        // TODO
        return false;
    }

    public void updateRequests(Set<ByteArrayWrapper> keys) {
        // TODO
    }
}
