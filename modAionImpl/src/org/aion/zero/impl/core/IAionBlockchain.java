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

package org.aion.zero.impl.core;

import java.util.List;
import org.aion.base.db.IRepository;
import org.aion.mcf.core.IBlockchain;
import org.aion.zero.impl.BlockContext;
import org.aion.zero.impl.sync.TrieDatabase;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;

/** aion blockchain interface. */
public interface IAionBlockchain
        extends IBlockchain<AionBlock, A0BlockHeader, AionTransaction, AionTxReceipt, AionTxInfo> {

    AionBlock createNewBlock(
            AionBlock parent, List<AionTransaction> transactions, boolean waitUntilBlockTime);

    BlockContext createNewBlockContext(
            AionBlock parent, List<AionTransaction> transactions, boolean waitUntilBlockTime);

    AionBlock getBestBlock();

    AionBlock getBlockByNumber(long num);

    /**
     * Recovery functionality for rebuilding the world state.
     *
     * @return {@code true} if the recovery was successful, {@code false} otherwise
     */
    boolean recoverWorldState(IRepository repository, AionBlock block);

    /**
     * Recovery functionality for recreating the block info in the index database.
     *
     * @return {@code true} if the recovery was successful, {@code false} otherwise
     */
    boolean recoverIndexEntry(IRepository repository, AionBlock block);

    /**
     * Heuristic for skipping the call to tryToConnect with very large or very small block number.
     */
    boolean skipTryToConnect(long blockNumber);

    /**
     * Retrieves the value for a given node from the database associated with the given type.
     *
     * @param key the key of the node to be retrieved
     * @param dbType the database where the key should be found
     * @return the {@code byte} array value associated with the given key or {@code null} when the
     *     key cannot be found in the database.
     * @throws IllegalArgumentException if the given key is null or the database type is not
     *     supported
     */
    byte[] getTrieNode(byte[] key, TrieDatabase dbType);
}
