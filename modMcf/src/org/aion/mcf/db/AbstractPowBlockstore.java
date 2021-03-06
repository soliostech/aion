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
package org.aion.mcf.db;

import org.aion.mcf.types.AbstractBlock;
import org.aion.mcf.types.AbstractBlockHeader;

/**
 * Abstract POW blockstore.
 *
 * @param <BLK>
 * @param <BH>
 */
public abstract class AbstractPowBlockstore<
                BLK extends AbstractBlock<?, ?>, BH extends AbstractBlockHeader>
        implements IBlockStorePow<BLK, BH> {

    @Override
    public byte[] getBlockHashByNumber(long blockNumber, byte[] branchBlockHash) {
        BLK branchBlock = getBlockByHash(branchBlockHash);
        if (branchBlock.getNumber() < blockNumber) {
            throw new IllegalArgumentException(
                    "Requested block number > branch hash number: "
                            + blockNumber
                            + " < "
                            + branchBlock.getNumber());
        }
        while (branchBlock.getNumber() > blockNumber) {
            branchBlock = getBlockByHash(branchBlock.getParentHash());
        }
        return branchBlock.getHash();
    }
}
