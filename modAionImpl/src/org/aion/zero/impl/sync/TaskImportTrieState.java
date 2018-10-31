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

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.Hex;
import org.aion.mcf.trie.TrieNodeResult;
import org.aion.zero.impl.AionBlockchainImpl;
import org.slf4j.Logger;

/**
 * Processes the received trie nodes that were requested. The thread is shut down once the fast sync
 * manager indicates that the full trie is the complete.
 *
 * @author Alexandra Roatis
 */
final class TaskImportTrieState implements Runnable {

    private final Logger log;
    private final FastSyncMgr fastSyncMgr;

    private final AionBlockchainImpl chain;
    private final BlockingQueue<TrieNodeWrapper> trieNodes;

    /**
     * Constructor.
     *
     * @param log logger for reporting execution information
     * @param chain the blockchain used by the application
     * @param trieNodes received trie nodes
     * @param fastSyncMgr manages the fast sync process and indicates when completeness is reached
     */
    TaskImportTrieState(
            final Logger log,
            final AionBlockchainImpl chain,
            final BlockingQueue<TrieNodeWrapper> trieNodes,
            final FastSyncMgr fastSyncMgr) {
        this.log = log;
        this.chain = chain;
        this.trieNodes = trieNodes;
        this.fastSyncMgr = fastSyncMgr;
    }

    @Override
    public void run() {
        // importing the trie state should be highest priority
        // since it is usually the longest process (on large blockchains)
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        while (!fastSyncMgr.isComplete()) {
            TrieNodeWrapper tnw;
            try {
                tnw = trieNodes.take();
            } catch (InterruptedException ex) {
                if (!fastSyncMgr.isComplete()) {
                    log.error("<import-trie-nodes: interrupted without shutdown request>", ex);
                }
                return;
            }

            // filter nodes that already match imported values
            Map<ByteArrayWrapper, byte[]> nodes = filterImported(tnw.getTrieNodes());

            // skip batch if everything already imported
            if (nodes.isEmpty()) {
                continue;
            }

            TrieDatabase dbType = tnw.getDbType();
            String peer = tnw.getDisplayId();
            ByteArrayWrapper key;
            byte[] value;
            boolean failed = false;

            for (Entry<ByteArrayWrapper, byte[]> e : nodes.entrySet()) {
                key = e.getKey();
                value = e.getValue();

                TrieNodeResult result = chain.importTrieNode(key.getData(), value, dbType);

                if (result.isSuccessful()) {
                    fastSyncMgr.addImportedNode(key, value);
                    log.debug(
                            "<import-trie-nodes: key={}, value length={}, db={}, result={}, peer={}>",
                            key,
                            value.length,
                            dbType,
                            result,
                            peer);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "<import-trie-nodes-failed: key={}, value={}, db={}, result={}, peer={}>",
                                key,
                                Hex.toHexString(value),
                                dbType,
                                result,
                                peer);
                    }
                    fastSyncMgr.handleFailedImport(key, value, dbType, tnw.getPeerId(), peer);
                    failed = true;
                    // exit this loop and ignore other imports
                    break;
                }
            }

            if (!failed) {
                // reexamine missing states and make further requests
                fastSyncMgr.updateRequests(nodes.keySet());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("<import-trie-nodes: shutdown>");
        }
    }

    /**
     * Filters out trie nodes that have been imported when both the key and the value match the
     * already imported data.
     *
     * @param trieNodes the initial set of trie nodes to be imported
     * @return the remaining nodes after the exact matches have been filtered out
     */
    private Map<ByteArrayWrapper, byte[]> filterImported(Map<ByteArrayWrapper, byte[]> trieNodes) {
        return trieNodes
                .entrySet()
                .parallelStream()
                .filter(e -> !fastSyncMgr.containsExact(e.getKey(), e.getValue()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }
}
