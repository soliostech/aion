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

import static org.aion.p2p.P2pConstant.BACKWARD_SYNC_STEP;
import static org.aion.p2p.P2pConstant.CLOSE_OVERLAPPING_BLOCKS;
import static org.aion.p2p.P2pConstant.FAR_OVERLAPPING_BLOCKS;
import static org.aion.p2p.P2pConstant.LARGE_REQUEST_SIZE;
import static org.aion.p2p.P2pConstant.REQUEST_SIZE;
import static org.aion.zero.impl.sync.PeerState.Mode.NORMAL;
import static org.aion.zero.impl.sync.PeerState.Mode.THUNDER;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Random;
import org.aion.p2p.INode;
import org.aion.zero.impl.sync.PeerStateMgr.NodeState;
import org.aion.zero.impl.sync.msg.ReqBlocksHeaders;
import org.slf4j.Logger;

/** @author chris */
final class TaskGetHeaders implements Runnable {

    private final long selfNumber;

    private final BigInteger selfTd;

    private final PeerStateMgr peerStateMgr;

    private final Logger log;

    private final Random random = new Random(System.currentTimeMillis());

    TaskGetHeaders(long selfNumber, BigInteger selfTd, PeerStateMgr peerStateMgr, Logger log) {
        this.selfNumber = selfNumber;
        this.selfTd = selfTd;
        this.peerStateMgr = peerStateMgr;
        this.log = log;
    }

    @Override
    public void run() {
        // filter nodes by total difficulty
        long now = System.currentTimeMillis();
        Optional<NodeState> anyFiltered = peerStateMgr.getAnyNodeForHeaderRequest(now, selfTd);

        if (!anyFiltered.isPresent()) {
            if (log.isDebugEnabled()) {
                log.debug("No peers were found.");
            }
            return;
        }

        // pick one random node
        INode node = anyFiltered.get().getNode();

        // fetch the peer state
        PeerState state = anyFiltered.get().getPeerState();

        // decide the start block number
        long from = 0;
        int size = REQUEST_SIZE;

        state.setLastBestBlock(node.getBestBlockNumber());

        switch (state.getMode()) {
            case LIGHTNING:
                {
                    // request far forward blocks
                    if (state.getBase() > selfNumber + LARGE_REQUEST_SIZE
                            // there have not been STEP_COUNT sequential requests
                            && state.isUnderRepeatThreshold()) {
                        size = LARGE_REQUEST_SIZE;
                        from = state.getBase();
                        break;
                    } else {
                        // transition to ramp down strategy
                        state.setMode(THUNDER);
                    }
                }
            case THUNDER:
                {
                    // there have not been STEP_COUNT sequential requests
                    if (state.isUnderRepeatThreshold()) {
                        state.setBase(selfNumber);
                        size = LARGE_REQUEST_SIZE;
                        from = Math.max(1, selfNumber - FAR_OVERLAPPING_BLOCKS);
                        break;
                    } else {
                        // behave as normal
                        state.setMode(NORMAL);
                    }
                }
            case NORMAL:
                {
                    // update base block
                    state.setBase(selfNumber);

                    // normal mode
                    long nodeNumber = node.getBestBlockNumber();
                    if (nodeNumber >= selfNumber + BACKWARD_SYNC_STEP) {
                        from = Math.max(1, selfNumber - FAR_OVERLAPPING_BLOCKS);
                    } else if (nodeNumber >= selfNumber - BACKWARD_SYNC_STEP) {
                        from = Math.max(1, selfNumber - CLOSE_OVERLAPPING_BLOCKS);
                    } else {
                        // no need to request from this node. His TD is probably corrupted.
                        return;
                    }
                    break;
                }
            case BACKWARD:
                {
                    int backwardStep;
                    // the randomness improves performance when
                    // multiple peers are on the side-chain
                    if (random.nextBoolean()) {
                        // step back by REQUEST_SIZE to BACKWARD_SYNC_STEP blocks
                        backwardStep = size * (random.nextInt(BACKWARD_SYNC_STEP / size) + 1);
                    } else {
                        // step back by BACKWARD_SYNC_STEP blocks
                        backwardStep = BACKWARD_SYNC_STEP;
                    }
                    from = Math.max(1, state.getBase() - backwardStep);
                    break;
                }
            case FORWARD:
                {
                    // start from base block
                    from = state.getBase() + 1;
                    break;
                }
        }

        // send request
        if (log.isDebugEnabled()) {
            log.debug(
                    "<get-headers mode={} from-num={} size={} node={}>",
                    state.getMode(),
                    from,
                    size,
                    node.getIdShort());
        }
        ReqBlocksHeaders rbh = new ReqBlocksHeaders(from, size);
        peerStateMgr.send(node.getIdHash(), node.getIdShort(), rbh);

        // update timestamp
        state.setLastHeaderRequest(now);
    }
}
