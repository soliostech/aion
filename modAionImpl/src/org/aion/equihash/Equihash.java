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

package org.aion.equihash;

import static org.aion.base.util.ByteUtil.merge;
import static org.aion.base.util.ByteUtil.toLEByteArray;
import static org.aion.base.util.Hex.toHexString;

import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.aion.base.util.NativeLoader;
import org.aion.crypto.HashUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

/**
 * This class serves as the front end interface to the Tromp Equihash solver accessed through JNI.
 * This class also contains methods to verify equihash solutions, either generated locally or
 * received from peers.
 *
 * @author Ross Kitsis (ross@nuco.io)
 */
public class Equihash {
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());

    private int cBitLen; // Collision Bit Length used by equihash
    AtomicLong totalSolGenerated;

    /*
     * Load native libraries
     */
    static {
        NativeLoader.loadLibrary("sodium");
        NativeLoader.loadLibrary("equihash");
    }

    public native int[][] solve(byte[] nonce, byte[] headerBytes);

    /**
     * Create a new Equihash instance with the parameters (n,k)
     *
     * @param n Total number of bits over which to do XOR collisions
     * @param k Number of steps with which to solve.
     */
    public Equihash(int n, int k) {
        this.cBitLen = n / (k + 1);
        this.totalSolGenerated = new AtomicLong(0);
    }

    /**
     * Retrieves a set of possible solutions given the passed header and nonce value Any number of
     * solutions may be returned; the maximum number of solutions observed has been 8
     *
     * @param header A 32 byte hash of the block header (minus nonce and solutions)
     * @param nonce - A 32 byte header
     * @return An array of equihash solutions
     */
    public int[][] getSolutionsForNonce(byte[] header, byte[] nonce) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Java generated header nonce: " + toHexString(merge(header, nonce)));
            LOG.debug("Size of header nonce: " + merge(header, nonce).length);
        }

        int[][] solutions = null;

        if (header != null && nonce != null) {
            // Call JNI to retrieve a solution
            solutions = this.solve(nonce, header);
        }
        return solutions;
    }

    /*
     * Mine for a single nonce
     */
    public Solution mine(IAionBlock block, byte[] nonce) {
        byte[] minimal = new byte[1408];
        ThreadLocalRandom.current().nextBytes(minimal);
        return new Solution(block, nonce, minimal);
    }

    /**
     * Checks if the solution meets difficulty requirements for this block.
     *
     * @param target Target under which hash must fall below
     * @return True is the solution meets target conditions; false otherwise.
     */
    private boolean isValidBlock(byte[] validationBytes, BigInteger target) {
        boolean isValid = false;

        // Default blake2b without personalization to test if hash is below
        // difficulty
        BigInteger hdrDigest = new BigInteger(1, HashUtil.h256(validationBytes));

        if (LOG.isDebugEnabled()) {
            LOG.debug("Comparing header digest {} to target {}: ", hdrDigest, target);
        }

        if (hdrDigest.compareTo(target) < 0) {
            isValid = true;
        }
        return isValid;
    }
}
