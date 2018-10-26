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

import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.trie.TrieNodeResult;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.AionBlockchainImpl;
import org.slf4j.Logger;

/**
 * Processes the received trie nodes that were requested. Ensures that the full trie is the
 * eventually received by tracking the completeness of the imports.
 *
 * <p>Requests the trie nodes in reasonable batches (TODO: define size).
 *
 * @author Alexandra Roatis
 */
final class TaskImportTrieState implements Runnable {

    private final Logger log;
    private final AtomicBoolean run;
    private final IP2pMgr p2p;

    private final AionBlockchainImpl chain;
    private final BlockingQueue<TrieNodeWrapper> trieNodes;

    private final Set<ByteArrayWrapper> importedTrieNodes;
    private final BlockingQueue<ByteArrayWrapper> requiredTrieNodes;

    /**
     * Constructor.
     *
     * @param log logger for reporting execution information
     * @param run used to indicate when thread is required to shutdown
     * @param p2p peer manager used to submit messages
     * @param chain the blockchain used by the application
     * @param trieNodes received trie nodes
     */
    TaskImportTrieState(
            final Logger log,
            final AtomicBoolean run,
            final IP2pMgr p2p,
            final AionBlockchainImpl chain,
            final BlockingQueue<TrieNodeWrapper> trieNodes,
            final Set<ByteArrayWrapper> importedTrieNodes,
            final BlockingQueue<ByteArrayWrapper> requiredTrieNodes) {
        this.log = log;
        this.run = run;
        this.p2p = p2p;
        this.chain = chain;
        this.trieNodes = trieNodes;
        this.importedTrieNodes = importedTrieNodes;
        // TODO: may be converted to local variable
        this.requiredTrieNodes = requiredTrieNodes;
    }

    @Override
    public void run() {
        // TODO: determine priority setting
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        while (run.get()) {
            TrieNodeWrapper tnw;
            try {
                tnw = trieNodes.take();
            } catch (InterruptedException ex) {
                if (run.get()) {
                    log.error("<import-trie-nodes: interrupted without shutdown request>", ex);
                }
                return;
            }

            // TODO: batch imports to chain
            for (Entry<ByteArrayWrapper, byte[]> e : tnw.getTrieNodes().entrySet()) {
                TrieNodeResult result =
                        chain.importTrieNode(e.getKey().getData(), e.getValue(), tnw.getDbType());

                if (result.isSuccessful()) {
                    // TODO: traverse state to determine further requirements
                } else {
                    if (log.isDebugEnabled()) {
                        // TODO: improve message
                        log.debug("Given value {} is incorrect or does not match known value {} ");
                    }

                    // TODO: received incorrect or inconsistent state: change pivot??
                }
            }

            // TODO: send state request to multiple peers

            // TODO: notify sync manager when complete state, storage and details

        }

        if (log.isDebugEnabled()) {
            log.debug("<import-trie-nodes: shutdown>");
        }
    }
}
