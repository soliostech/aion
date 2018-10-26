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

package org.aion.zero.impl.sync.handler;

import java.util.concurrent.BlockingQueue;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.TrieDatabase;
import org.aion.zero.impl.sync.TrieNodeWrapper;
import org.aion.zero.impl.sync.msg.ResponseTrieState;
import org.slf4j.Logger;

/**
 * Handler for trie node responses from the network.
 *
 * @author Alexandra Roatis
 */
public final class ResponseTrieStateHandler extends Handler {

    private final Logger log;

    private final BlockingQueue<TrieNodeWrapper> states;

    /**
     * Constructor.
     *
     * @param log logger for reporting execution information
     * @param states map containing the received states to be processed
     */
    public ResponseTrieStateHandler(final Logger log, final BlockingQueue<TrieNodeWrapper> states) {
        super(Ver.V1, Ctrl.SYNC, Act.RESPONSE_TRIE_STATE);
        this.log = log;
        this.states = states;
    }

    @Override
    public void receive(int peerId, String displayId, final byte[] message) {
        if (message == null || message.length == 0) {
            this.log.debug("<res-trie empty message from peer={}>", displayId);
            return;
        }

        ResponseTrieState response = ResponseTrieState.decode(message);

        if (response != null) {
            TrieDatabase dbType = response.getDbType();
            byte[] key = response.getNodeKey();
            byte[] value = response.getNodeValue();

            if (log.isDebugEnabled()) {
                this.log.debug(
                        "<res-trie from-db={} key={} value={} peer={}>",
                        dbType,
                        ByteArrayWrapper.wrap(key),
                        ByteArrayWrapper.wrap(value),
                        displayId);
            }

            if (key != null && value != null) {
                states.add(
                        new TrieNodeWrapper(
                                peerId, displayId, dbType, ByteArrayWrapper.wrap(key), value));
            }
        } else {
            this.log.error("<res-trie decode-error msg-bytes={} peer={}>", message.length, peerId);
        }
    }
}
