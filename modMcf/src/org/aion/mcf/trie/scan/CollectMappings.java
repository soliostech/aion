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

package org.aion.mcf.trie.scan;

import java.util.HashMap;
import java.util.Map;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.rlp.Value;

/** @author Alexandra Roatis */
public class CollectMappings implements ScanAction {

    Map<ByteArrayWrapper, byte[]> nodes = new HashMap<>();

    @Override
    public void doOnNode(byte[] hash, Value node) {
        // todo: value bytes??
        nodes.put(new ByteArrayWrapper(hash), node.asBytes());
    }

    public Map<ByteArrayWrapper, byte[]> getNodes() {
        return nodes;
    }

    public int getSize() {
        return nodes.size();
    }
}
