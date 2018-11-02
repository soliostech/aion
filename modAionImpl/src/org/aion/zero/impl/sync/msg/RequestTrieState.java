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

package org.aion.zero.impl.sync.msg;

import java.util.Objects;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.rlp.Value;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.TrieDatabase;

/**
 * Request message for a trie node from a specific blockchain database.
 *
 * @author Alexandra Roatis
 */
public final class RequestTrieState extends Msg {
    private final TrieDatabase dbType;
    private final byte[] nodeKey;

    /**
     * Constructor for trie node requests.
     *
     * @param nodeKey the key of the requested trie node
     * @param dbType the blockchain database in which the key should be found
     * @throws NullPointerException if either of the given parameters is {@code null}
     */
    public RequestTrieState(final byte[] nodeKey, final TrieDatabase dbType) {
        super(Ver.V1, Ctrl.SYNC, Act.REQUEST_TRIE_STATE);

        // ensure inputs are not null
        Objects.requireNonNull(nodeKey);
        Objects.requireNonNull(dbType);

        this.nodeKey = nodeKey;
        this.dbType = dbType;
    }

    /**
     * Decodes a message into a trie node request.
     *
     * @param message a {@code byte} array representing a request for a trie node.
     * @return the decoded trie node request.
     */
    public static RequestTrieState decode(final byte[] message) {
        if (message == null || message.length == 0) {
            return null;
        } else {
            RLPList list = (RLPList) RLP.decode2(message).get(0);
            if (list.size() != 2) {
                return null;
            } else {
                // decode the db type
                Value type = Value.fromRlpEncoded(list.get(0).getRLPData());
                TrieDatabase dbType;
                if (!type.isString()) {
                    return null;
                } else {
                    try {
                        dbType = TrieDatabase.valueOf(type.asString());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }

                // decode the key
                Value hash = Value.fromRlpEncoded(list.get(1).getRLPData());
                if (!hash.isBytes() || hash.asBytes().length != 32) {
                    return null;
                }

                return new RequestTrieState(hash.asBytes(), dbType);
            }
        }
    }

    @Override
    public byte[] encode() {
        return RLP.encodeList(RLP.encodeString(dbType.toString()), RLP.encodeElement(nodeKey));
    }

    /**
     * Returns the blockchain database in which the requested key should be found.
     *
     * @return the blockchain database in which the requested key should be found.
     */
    public TrieDatabase getDbType() {
        return dbType;
    }

    /**
     * Returns the key of the requested trie node.
     *
     * @return the key of the requested trie node.
     */
    public byte[] getNodeKey() {
        return nodeKey;
    }
}
