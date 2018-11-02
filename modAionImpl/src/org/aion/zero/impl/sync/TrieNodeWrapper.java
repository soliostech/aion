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
import org.aion.base.util.ByteArrayWrapper;
import org.aion.zero.impl.sync.msg.ResponseTrieState;

/**
 * Container for received trie node requests.
 *
 * @author Alexandra Roatis
 */
public final class TrieNodeWrapper {

    private final int peerId;
    private final String displayId;
    private final ResponseTrieState data;

    /**
     * Constructor.
     *
     * @param peerId the hash id of the peer who sent the response
     * @param displayId the display id of the peer who sent the response
     * @param data the response received from the peer containing the trie node data
     */
    public TrieNodeWrapper(final int peerId, final String displayId, final ResponseTrieState data) {
        this.peerId = peerId;
        this.displayId = displayId;
        this.data = data;
    }

    /**
     * Returns the hash id of the peer who sent the response.
     *
     * @return the hash id of the peer who sent the response.
     */
    public int getPeerId() {
        return peerId;
    }

    /**
     * Returns the display id of the peer who sent the response.
     *
     * @return the display id of the peer who sent the response.
     */
    public String getDisplayId() {
        return displayId;
    }

    /**
     * Returns the blockchain database in which the requested key was found.
     *
     * @return the blockchain database in which the requested key was found.
     */
    public TrieDatabase getDbType() {
        return data.getDbType();
    }

    /**
     * Returns the key-value pairs for the requested trie node.
     *
     * @return the key-value pairs for the requested trie node.
     */
    public Map<ByteArrayWrapper, byte[]> getReferencedNodes() {
        return data.getReferencedNodes();
    }

    /**
     * Returns the key of the requested trie node.
     *
     * @return the key of the requested trie node.
     */
    public ByteArrayWrapper getNodeKey() {
        return data.getNodeKey();
    }

    /**
     * Returns the value stored for the requested trie node.
     *
     * @return the value stored for the requested trie node.
     */
    public byte[] getNodeValue() {
        return data.getNodeValue();
    }
}
