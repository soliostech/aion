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
package org.aion.mcf.core;

import com.google.common.base.Objects;
import org.aion.mcf.types.AbstractTransaction;
import org.aion.mcf.types.AbstractTxReceipt;

import java.util.Arrays;

/**
 * Abstract transaction info.
 *
 * @param <TXR>
 * @param <TX>
 */
public abstract class AbstractTxInfo<
        TXR extends AbstractTxReceipt<?>, TX extends AbstractTransaction> {

    protected TXR receipt;

    protected byte[] blockHash;

    protected byte[] parentBlockHash;

    protected int index;

    public abstract void setTransaction(TX tx);

    public abstract byte[] getEncoded();

    public abstract TXR getReceipt();

    public abstract byte[] getBlockHash();

    public abstract byte[] getParentBlockHash();

    public abstract void setParentBlockHash(byte[] hash);

    public abstract int getIndex();

    public abstract boolean isPending();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractTxInfo)) return false;
        AbstractTxInfo<?, ?> that = (AbstractTxInfo<?, ?>) o;
        return index == that.index &&
                Objects.equal(receipt, that.receipt) &&
                Arrays.equals(blockHash, that.blockHash) &&
                Arrays.equals(parentBlockHash, that.parentBlockHash);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(receipt, blockHash, parentBlockHash, index);
    }
}
