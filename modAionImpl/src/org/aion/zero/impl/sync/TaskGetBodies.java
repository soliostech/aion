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

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.aion.zero.impl.sync.PeerState.State;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.aion.zero.types.A0BlockHeader;
import org.slf4j.Logger;

/**
 * long run
 *
 * @author chris
 */
final class TaskGetBodies implements Runnable {

    private final AtomicBoolean run;

    private final BlockingQueue<HeadersWrapper> downloadedHeaders;

    private final ConcurrentHashMap<Integer, HeadersWrapper> headersWithBodiesRequested;

    private final PeerStateMgr peerStateMgr;

    private final Logger log;

    /**
     * @param _run AtomicBoolean
     * @param _downloadedHeaders BlockingQueue
     * @param _headersWithBodiesRequested ConcurrentHashMap
     * @param _peerStateMgr PeerStateMgr
     * @param _log Logger
     */
    TaskGetBodies(
            final AtomicBoolean _run,
            final BlockingQueue<HeadersWrapper> _downloadedHeaders,
            final ConcurrentHashMap<Integer, HeadersWrapper> _headersWithBodiesRequested,
            final PeerStateMgr _peerStateMgr,
            final Logger _log) {
        this.run = _run;
        this.downloadedHeaders = _downloadedHeaders;
        this.headersWithBodiesRequested = _headersWithBodiesRequested;
        this.peerStateMgr = _peerStateMgr;
        this.log = _log;
    }

    @Override
    public void run() {
        while (run.get()) {
            HeadersWrapper hw;
            try {
                hw = downloadedHeaders.take();
            } catch (InterruptedException e) {
                continue;
            }

            int idHash = hw.getNodeIdHash();
            String displayId = hw.getDisplayId();
            List<A0BlockHeader> headers = hw.getHeaders();
            if (headers.isEmpty()) {
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug(
                        "<get-bodies from-num={} to-num={} node={}>",
                        headers.get(0).getNumber(),
                        headers.get(headers.size() - 1).getNumber(),
                        displayId);
            }

            peerStateMgr.send(
                    idHash,
                    displayId,
                    new ReqBlocksBodies(
                            headers.stream().map(k -> k.getHash()).collect(Collectors.toList())));
            headersWithBodiesRequested.put(idHash, hw);

            if (!peerStateMgr.updateState(idHash, State.BODIES_REQUESTED)) {
                // message logged when update fails
                log.warn("Peer {} sent blocks that were not requested.", displayId);
            }
        }
    }
}
