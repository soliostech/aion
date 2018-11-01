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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;

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

    private final Map<ByteArrayWrapper, byte[]> importedTrieNodes = new ConcurrentHashMap<>();
    private final BlockingQueue<ByteArrayWrapper> requiredTrieNodes = new LinkedBlockingQueue<>();
    private final BlockingQueue<TrieNodeWrapper> receivedTrieNodes = new LinkedBlockingQueue<>();

    private final AtomicBoolean complete = new AtomicBoolean(false);
    private AionBlock pivot = null;
    private final AionBlockchainImpl chain;

    // TODO: define the trie depth for each request to set the batch size

    public FastSyncMgr(AionBlockchainImpl chain) {
        this.chain = chain;
    }

    public void addImportedNode(ByteArrayWrapper key, byte[] value) {
        importedTrieNodes.put(key, value);
    }

    public boolean containsExact(ByteArrayWrapper key, byte[] value) {
        return importedTrieNodes.containsKey(key)
                && Arrays.equals(importedTrieNodes.get(key), value);
    }

    private void initializePivot() {
        // from pending store grab the first status that was received multiple time
        pivot = null; // TODO: chain.findAPivot()
    }

    /** Changes the pivot in case of import failure. */
    public void handleFailedImport(
            ByteArrayWrapper key, byte[] value, TrieDatabase dbType, int peerId, String peer) {
        // TODO: received incorrect or inconsistent state: change pivot??
        // TODO: consider case where someone purposely sends incorrect values
        // TODO: decide on how far back to move the pivot
    }

    public BlockingQueue<TrieNodeWrapper> getReceivedTrieNodes() {
        return receivedTrieNodes;
    }

    /**
     * Indicates the status of the fast sync process.
     *
     * @return {@code true} when fast sync is complete and secure, {@code false} while trie nodes
     *     are still required or completeness has not been confirmed yet
     */
    public boolean isComplete() {
        return complete.get();
    }

    /**
     * Checks that all the conditions for completeness are fulfilled.
     *
     * @implNote Expensive functionality which should not be called frequently.
     */
    private void ensureCompleteness() {
        // already complete, do nothing
        if (isComplete()) {
            return;
        }

        // TODO: determine most efficient ordering of conditions
        // TODO: make distinction between requirements of light clients and full nodes

        // ensure all blocks were received
        if (!isCompleteBlockData()) {
            return;
        }

        // ensure all transaction receipts were received
        if (!isCompleteReceiptData()) {
            return;
        }

        // ensure complete world state for pivot was received
        if (!isCompleteWorldState()) {
            return;
        }

        // ensure complete storage data was received
        if (!isCompleteStorage()) {
            return;
        }

        // ensure complete contract details data was received
        if (!isCompleteContractDetails()) {
            return;
        }

        // everything is complete
        complete.set(true);
    }

    private boolean isCompleteBlockData() {
        // TODO: block requests should be made backwards from pivot
        // TODO: requests need to be based on hash instead of level
        return false;
    }

    private boolean isCompleteReceiptData() {
        // TODO: implemented on separate branch
        return false;
    }

    private boolean isCompleteWorldState() {
        if (pivot == null) {
            return false;
        } else {
            // get root of pivot
            byte[] root = pivot.getStateRoot();

            Set<ByteArrayWrapper> missing = chain.traverseTrieFromNode(root, TrieDatabase.STATE);

            if (missing.isEmpty()) {
                return true;
            } else {
                requiredTrieNodes.addAll(missing);
                return false;
            }
        }
    }

    private boolean isCompleteStorage() {
        // TODO
        return false;
    }

    private boolean isCompleteContractDetails() {
        // TODO
        return false;
    }

    public void updateRequests(Set<ByteArrayWrapper> keys) {
        // TODO: check what's still missing and send out requests
        // TODO: send state request to multiple peers
    }
}
