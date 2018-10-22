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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Msg;
import org.aion.zero.impl.sync.PeerState.State;
import org.slf4j.Logger;

/**
 * Manages access to the peers and their sync states.
 *
 * @author Alexandra Roatis
 */
public class PeerStateMgr {

    private static final long EXPECTED_TIME_DIFF = 5000L;

    private final Map<Integer, PeerState> peerStates;

    private final Logger log;
    private final IP2pMgr p2p;
    private final SortedSet<NodeState> lightningStates;

    public PeerStateMgr(IP2pMgr p2p, Logger log) {
        this.p2p = p2p;
        this.peerStates = new ConcurrentHashMap<>();
        this.lightningStates = new ConcurrentSkipListSet<>();
        this.log = log;
    }

    /**
     * High priority getter that can retrieve locked states. Used by the threads importing blocks.
     *
     * @param peerID
     * @return {@code null} when the peer id does not have an associated state.
     */
    public PeerState getForImport(int peerID) {
        // ensures that no one else has access to the instance
        // without crating additional objects
        //        vessel.copy(peerStates.get(peerID));
        //        return vessel;
        return peerStates.get(peerID);
        //        } finally {
        //            lock.readLock().unlock();
        //        }
    }

    public Collection<PeerState> values() {
        return peerStates.values();
    }

    // TODO: enforce immutability
    public Map<Integer, PeerState> map() {
        return peerStates;
    }

    /** Checks that the peer's total difficulty is higher than or equal to the local chain. */
    private boolean isAdequateTotalDifficulty(final INode n, final BigInteger selfTd) {
        return n.getTotalDifficulty() != null && n.getTotalDifficulty().compareTo(selfTd) >= 0;
    }

    /** Checks that the required time has passed since the last request. */
    private boolean isTimelyRequest(long now, INode n) {
        PeerState state = peerStates.computeIfAbsent(n.getIdHash(), k -> new PeerState());
        return (now - EXPECTED_TIME_DIFF) > state.getLastHeaderRequest();
    }

    public Optional<NodeState> getAnyNodeForHeaderRequest(
            final long now, BigInteger td, Random random) {

        NodeState nodeState = lightningStates.isEmpty() ? null : lightningStates.first();
        boolean removed = nodeState != null && lightningStates.remove(nodeState);

        if (removed) {
            return Optional.of(nodeState);
        } else {
            // filter nodes by total difficulty
            List<INode> filtered =
                    getActiveNodes()
                            .parallelStream()
                            .filter(n -> isAdequateTotalDifficulty(n, td))
                            .filter(n -> isTimelyRequest(now, n))
                            .collect(Collectors.toList());

            if (filtered.isEmpty()) {
                return Optional.empty();
            } else {
                INode node = filtered.remove(random.nextInt(filtered.size()));
                PeerState state = peerStates.get(node.getIdHash());
                if (state != null) {
                    nodeState = new NodeState(node, peerStates.get(node.getIdHash()));
                } else {
                    nodeState = null;
                }

                if (lightningStates.isEmpty()) {
                    populateLightningStates(filtered);
                }

                return Optional.ofNullable(nodeState);
            }
        }
    }

    private void populateLightningStates(List<INode> nodesFiltered) {
        // TODO: maybe make separate thread
        for (INode node : nodesFiltered) {
            PeerState state = peerStates.get(node.getIdHash());
            if (state != null && state.isInLightningMode()) {
                lightningStates.add(new NodeState(node, state));
            }
        }
    }

    /** return true if successful */
    public boolean updateState(int nodeIdHash, State newState) {
        PeerState p = peerStates.get(nodeIdHash);
        if (p != null) {
            p.setState(newState);
            return true;
        }
        return false;
    }

    public void send(int idHash, String idShort, Msg rbh) {
        this.p2p.send(idHash, idShort, rbh);
    }

    public Collection<INode> getActiveNodes() {
        return p2p.getActiveNodes().values();
    }

    public String dumpPeerStateInfo() {
        // TODO requires locks
        List<NodeState> sorted = new ArrayList<>();
        for (INode n : getActiveNodes()) {
            PeerState s = peerStates.get(n.getIdHash());
            if (s != null) {
                sorted.add(new NodeState(n, s));
            }
        }

        if (!sorted.isEmpty()) {
            sorted.sort(
                    (n1, n2) ->
                            Long.compare(n2.getPeerState().getBase(), n1.getPeerState().getBase()));

            StringBuilder sb = new StringBuilder();
            sb.append("\n");
            sb.append(
                    "======================================================================== sync-status =========================================================================\n");
            sb.append(
                    String.format(
                            "%9s %16s %17s %8s %16s %2s %16s\n",
                            "id", "# best block", "state", "mode", "base", "rp", "last request"));
            sb.append(
                    "--------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");

            for (NodeState ns : sorted) {
                INode n = ns.getNode();
                PeerState s = ns.getPeerState();

                sb.append(
                        String.format(
                                "id:%6s %16d %17s %8s %16d %2d %16d\n",
                                n.getIdShort(),
                                n.getBestBlockNumber(),
                                s.getState(),
                                s.getMode(),
                                s.getBase(),
                                s.getRepeated(),
                                s.getLastHeaderRequest()));
            }
            return sb.toString();
        }
        return "";
    }

    /** Contains both a {@link INode} and it's associated {@link PeerState}. */
    public static class NodeState implements Comparable<NodeState> {
        final INode node;
        final PeerState peerState;

        // TODO: document that peerState cannot be null
        NodeState(INode node, PeerState peerState) {
            this.node = node;
            this.peerState = peerState;
        }

        public INode getNode() {
            return node;
        }

        public PeerState getPeerState() {
            return peerState;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            NodeState nodeState = (NodeState) o;
            return Objects.equals(node, nodeState.node)
                    && Objects.equals(peerState, nodeState.peerState);
        }

        @Override
        public int hashCode() {

            return Objects.hash(node, peerState);
        }

        @Override
        public int compareTo(NodeState other) {
            if (other == null) {
                return 1;
            }
            return Long.compare(peerState.getBase(), other.peerState.getBase());
        }
    }
}
