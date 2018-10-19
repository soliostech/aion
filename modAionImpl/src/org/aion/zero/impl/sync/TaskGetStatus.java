/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * The aion network project leverages useful source code from other
 * open source projects. We greatly appreciate the effort that was
 * invested in these projects and we thank the individual contributors
 * for their work. For provenance information and contributors
 * please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * Aion foundation.
 */

package org.aion.zero.impl.sync;

import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.INode;
import org.aion.zero.impl.sync.msg.ReqStatus;
import org.slf4j.Logger;

/**
 * long run
 *
 * @author chris
 */
final class TaskGetStatus implements Runnable {

    private static final int interval = 2000; // two seconds

    private static final ReqStatus reqStatus = new ReqStatus();

    private final AtomicBoolean run;

    private final PeerStateMgr peerStateMgr;

    private final Logger log;

    /**
     * @param _run AtomicBoolean
     * @param _peerStateMgr PeerStateMgr
     * @param _log Logger
     */
    TaskGetStatus(final AtomicBoolean _run, final PeerStateMgr _peerStateMgr, final Logger _log) {
        this.run = _run;
        this.peerStateMgr = _peerStateMgr;
        this.log = _log;
    }

    @Override
    public void run() {
        while (this.run.get()) {
            try {
                // Set<Integer> ids = new HashSet<>(p2p.getActiveNodes().keySet());
                for (INode n : peerStateMgr.getActiveNodes()) {
                    // System.out.println("requesting-status from-node=" + node.getIdShort());
                    peerStateMgr.send(n.getIdHash(), n.getIdShort(), reqStatus);
                }
                Thread.sleep(interval);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    // we were asked to quit
                    break;
                } else {
                    log.error("<sync-gs exception=" + e.toString() + ">");
                }
            }
        }
        log.info("<sync-gs shutdown>");
    }
}
