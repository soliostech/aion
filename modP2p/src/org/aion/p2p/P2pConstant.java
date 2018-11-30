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

package org.aion.p2p;

public class P2pConstant {

    public static final int //
            STOP_CONN_AFTER_FAILED_CONN = 8, //
            FAILED_CONN_RETRY_INTERVAL = 3000, //
            BAN_CONN_RETRY_INTERVAL = 30_000, //
            MAX_BODY_SIZE = 2 * 1024 * 1024 * 32, //
            RECV_BUFFER_SIZE = 8192 * 1024, //
            SEND_BUFFER_SIZE = 8192 * 1024, //

            // max p2p in package capped at 1.
            READ_MAX_RATE = 1,

            // max p2p in package capped for tx broadcast.
            READ_MAX_RATE_TXBC = 20,

            // write queue timeout
            WRITE_MSG_TIMEOUT = 5000,
            REQUEST_SIZE = 24,
            LARGE_REQUEST_SIZE = 40,

            // fast sync constants
            REQUIRED_CONNECTIONS = 6,
            PIVOT_DISTANCE_TO_HEAD = 1024,
            PIVOT_RESET_DISTANCE = 2 * PIVOT_DISTANCE_TO_HEAD,

            /**
             * The number of blocks overlapping with the current chain requested at import when the
             * local best block is far from the top block in the peer's chain.
             */
            FAR_OVERLAPPING_BLOCKS = 3,

            /**
             * The number of blocks overlapping with the current chain requested at import when the
             * local best block is close to the top block in the peer's chain.
             */
            CLOSE_OVERLAPPING_BLOCKS = 15,
            STEP_COUNT = 6,
            MIN_NORMAL_PEERS = 4,
            MAX_NORMAL_PEERS = 16,
            COEFFICIENT_NORMAL_PEERS = 2,

            // NOTE: the 3 values below are interdependent
            // do not change one without considering the impact to the others
            BACKWARD_SYNC_STEP = REQUEST_SIZE * STEP_COUNT - 1;
}
