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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.rlp.Value;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.TrieDatabase;

/**
 * Response message for a trie node node from a specific blockchain database.
 *
 * @author Alexandra Roatis
 */
public final class ResponseTrieState extends Msg {
    private final TrieDatabase dbType;
    private final ByteArrayWrapper nodeKey; // data of 32 bytes
    private final byte[] nodeValue;
    private final Map<ByteArrayWrapper, byte[]> referencedNodes; // empty for leaf nodes

    /**
     * Constructor for trie node responses that represent leafs, i.e. a single key-value pair.
     *
     * @param nodeKey the key of the requested trie node
     * @param nodeValue the value stored for the requested trie node
     * @param dbType the blockchain database in which the key should be found
     * @throws NullPointerException if any of the given parameters are {@code null}
     */
    public ResponseTrieState(
            final byte[] nodeKey, final byte[] nodeValue, final TrieDatabase dbType) {
        super(Ver.V1, Ctrl.SYNC, Act.RESPONSE_TRIE_STATE);

        // ensure inputs are not null
        Objects.requireNonNull(nodeKey);
        Objects.requireNonNull(nodeValue);
        Objects.requireNonNull(dbType);

        this.nodeKey = ByteArrayWrapper.wrap(nodeKey);
        this.nodeValue = nodeValue;
        this.referencedNodes = new HashMap<>();
        this.dbType = dbType;
    }

    /**
     * Constructor for trie node responses that contain multiple nodes (key-value pairs).
     *
     * @param nodeKey the key of the requested trie node
     * @param nodeValue the value stored for the requested trie node
     * @param referencedNodes a map of key-value pairs referenced by the value of the requested key
     * @param dbType the blockchain database in which the key should be found
     * @throws NullPointerException if any of the given parameters are {@code null}
     */
    private ResponseTrieState(
            final ByteArrayWrapper nodeKey,
            final byte[] nodeValue,
            final Map<ByteArrayWrapper, byte[]> referencedNodes,
            final TrieDatabase dbType) {
        super(Ver.V1, Ctrl.SYNC, Act.RESPONSE_TRIE_STATE);

        // ensure inputs are not null
        Objects.requireNonNull(nodeKey);
        Objects.requireNonNull(nodeValue);
        Objects.requireNonNull(dbType);
        Objects.requireNonNull(referencedNodes);

        this.nodeKey = nodeKey;
        this.nodeValue = nodeValue;
        this.referencedNodes = referencedNodes;
        this.dbType = dbType;
    }

    /**
     * Decodes a message into a trie node response.
     *
     * @param message a {@code byte} array representing a response to a trie node request.
     * @return the decoded trie node response.
     * @implNote The decoder must return {@code null} if any of the component values are {@code
     *     null} or invalid.
     */
    public static ResponseTrieState decode(final byte[] message) {
        if (message == null || message.length == 0) {
            return null;
        } else {
            RLPList list = (RLPList) RLP.decode2(message).get(0);
            if (list.size() != 4) {
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

                // decode the value
                Value value = Value.fromRlpEncoded(list.get(2).getRLPData());
                if (!value.isBytes() || value.asBytes().length == 0) {
                    return null;
                }

                // decode the referenced nodes
                RLPElement referenced = list.get(3);
                if (!(referenced instanceof RLPList)) {
                    return null;
                }
                Map<ByteArrayWrapper, byte[]> nodes = decodeReferencedNodes((RLPList) referenced);
                if (nodes == null) {
                    return null;
                }

                return new ResponseTrieState(
                        ByteArrayWrapper.wrap(hash.asBytes()), value.asBytes(), nodes, dbType);
            }
        }
    }

    /**
     * Decodes the list of key-value pair encodings into a map representation.
     *
     * @return a map of all the key-value pair encodings.
     */
    private static Map<ByteArrayWrapper, byte[]> decodeReferencedNodes(RLPList referenced) {
        if (referenced.isEmpty()) {
            return Collections.emptyMap();
        }

        RLPList current;
        Value hash, value;
        Map<ByteArrayWrapper, byte[]> nodes = new HashMap<>();

        for (RLPElement pair : referenced) {
            if (!(pair instanceof RLPList)) {
                return null;
            }
            current = (RLPList) pair;

            // decode the key
            hash = Value.fromRlpEncoded(current.get(0).getRLPData());
            if (!hash.isBytes() || hash.asBytes().length != 32) {
                return null;
            }

            // decode the value
            value = Value.fromRlpEncoded(current.get(1).getRLPData());
            if (!value.isBytes() || value.asBytes().length == 0) {
                return null;
            }

            nodes.put(new ByteArrayWrapper(hash.asBytes()), value.asBytes());
        }

        return nodes;
    }

    @Override
    public byte[] encode() {
        return RLP.encodeList(
                RLP.encodeString(dbType.toString()),
                RLP.encodeElement(nodeKey.getData()),
                RLP.encodeElement(nodeValue),
                RLP.encodeList(encodeReferencedNodes()));
    }

    /**
     * Encodes each key-value pair from the {@link #referencedNodes} as a list of two elements.
     *
     * @return an array of all the key-value pair encodings.
     */
    private byte[][] encodeReferencedNodes() {
        byte[][] pairs = new byte[referencedNodes.size()][];

        int i = 0;
        for (Map.Entry<ByteArrayWrapper, byte[]> e : referencedNodes.entrySet()) {
            pairs[i++] =
                    RLP.encodeList(
                            RLP.encodeElement(e.getKey().getData()),
                            RLP.encodeElement(e.getValue()));
        }

        return pairs;
    }

    /**
     * Returns the blockchain database in which the requested key was found.
     *
     * @return the blockchain database in which the requested key was found.
     */
    public TrieDatabase getDbType() {
        return dbType;
    }

    /**
     * Returns the key of the requested trie node.
     *
     * @return the key of the requested trie node.
     */
    public ByteArrayWrapper getNodeKey() {
        return nodeKey;
    }

    /**
     * Returns the value stored for the requested trie node.
     *
     * @return the value stored for the requested trie node.
     */
    public byte[] getNodeValue() {
        return nodeValue;
    }

    /**
     * Returns the list of RLP encoded key-value pairs referenced by the value of the requested key.
     *
     * @return the list of RLP encoded key-value pairs referenced by the value of the requested key.
     */
    public Map<ByteArrayWrapper, byte[]> getReferencedNodes() {
        return referencedNodes;
    }

    @Override
    public String toString() {
        return "{db="
                + dbType
                + ", key="
                + nodeKey
                + ", value="
                + nodeValue.length
                + ", refs="
                + referencedNodes.size()
                + '}';
    }
}
