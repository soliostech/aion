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
package org.aion.zero.impl.db;

import static org.aion.mcf.db.DatabaseUtils.connectAndOpen;
import static org.aion.mcf.db.DatabaseUtils.verifyAndBuildPath;
import static org.aion.p2p.P2pConstant.LARGE_REQUEST_SIZE;
import static org.aion.p2p.P2pConstant.STEP_COUNT;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.base.db.Flushable;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory.Props;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.db.exception.InvalidFilePathException;
import org.aion.mcf.ds.ObjectDataSource;
import org.aion.mcf.ds.Serializer;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 * Class for storing blocks that are correct but cannot be imported due to missing parent. Used to
 * speed up lightning sync and backward sync to side chains.
 *
 * <p>The blocks are stored using three data sources:
 *
 * <ul>
 *   <li><b>levels</b>: maps a blockchain height to the queue identifiers that start with blocks at
 *       that height;
 *   <li><b>queues</b>: maps queues identifiers to the list of blocks (in ascending order) that
 *       belong to the queue;
 *   <li><b>indexes</b>: maps block hashes to the identifier of the queue where the block is stored.
 * </ul>
 *
 * Additionally, the class is used to optimize requests for blocks ahead of time by tracking
 * received status blocks and proposing (mostly non-overlapping) base values for the requests.
 *
 * @author Alexandra Roatis
 */
public class PendingBlockStore implements Flushable, Closeable {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());
    private static final Logger LOG_SYNC = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // database names
    private static final String LEVEL_DB_NAME = "level";
    private static final String QUEUE_DB_NAME = "queue";
    private static final String INDEX_DB_NAME = "index";

    // data sources
    /**
     * Used to map a level (blockchain height) to the queue identifiers that start with blocks at
     * that height.
     */
    private ObjectDataSource<List<byte[]>> levelSource;
    /** Used to map a queue identifier to a list of consecutive blocks. */
    private ObjectDataSource<List<AionBlock>> queueSource;
    /** Used to maps a block hash to its current queue identifier. */
    private IByteArrayKeyValueDatabase indexSource;

    // tracking the status
    private Map<ByteArrayWrapper, QueueInfo> status;
    private long maxRequest = 0L, minStatus = Long.MAX_VALUE, maxStatus = 0L;

    private static final int FORWARD_SKIP = STEP_COUNT * LARGE_REQUEST_SIZE;

    /**
     * Constructor. Initializes the databases used for storage. If the database configuration used
     * requires persistence, the constructor ensures the path can be accessed or throws an exception
     * if persistence is requested but not achievable.
     *
     * @param props properties of the databases to be used for storage
     * @throws InvalidFilePathException when given a persistent database vendor for which the path
     *     cannot be created
     */
    public PendingBlockStore(Properties props) throws InvalidFilePathException {
        // check for database persistence requirements
        DBVendor vendor = DBVendor.fromString(props.getProperty(Props.DB_TYPE));
        if (vendor.getPersistence()) {
            File pbFolder =
                    new File(props.getProperty(Props.DB_PATH), props.getProperty(Props.DB_NAME));

            verifyAndBuildPath(pbFolder);
            props.setProperty(Props.DB_PATH, pbFolder.getAbsolutePath());
        }

        init(props);
    }

    /**
     * Initializes and opens the databases where the pending blocks will be stored.
     *
     * @param props the database properties to be used in initializing the underlying databases
     */
    private void init(Properties props) {
        // initialize status
        this.status = new HashMap<>();

        IByteArrayKeyValueDatabase database;

        // create the level source
        props.setProperty(Props.DB_NAME, LEVEL_DB_NAME);
        database = connectAndOpen(props, LOG);
        this.levelSource = new ObjectDataSource<>(database, HASH_LIST_RLP_SERIALIZER);

        // create the queue source
        props.setProperty(Props.DB_NAME, QUEUE_DB_NAME);
        database = connectAndOpen(props, LOG);
        this.queueSource = new ObjectDataSource<>(database, BLOCK_LIST_RLP_SERIALIZER);

        // create the index source
        props.setProperty(Props.DB_NAME, INDEX_DB_NAME);
        this.indexSource = connectAndOpen(props, LOG);
    }

    /**
     * Checks that the underlying storage was correctly initialized and open.
     *
     * @return true if correctly initialized and the databases are open, false otherwise.
     */
    public boolean isOpen() {
        return status != null
                && levelSource.isOpen()
                && queueSource.isOpen()
                && indexSource.isOpen();
    }

    private static final Serializer<List<byte[]>, byte[]> HASH_LIST_RLP_SERIALIZER =
            new Serializer<>() {
                @Override
                public byte[] serialize(List<byte[]> object) {
                    byte[][] infoList = new byte[object.size()][];
                    int i = 0;
                    for (byte[] b : object) {
                        infoList[i] = RLP.encodeElement(b);
                        i++;
                    }
                    return RLP.encodeList(infoList);
                }

                @Override
                public List<byte[]> deserialize(byte[] stream) {
                    RLPList list = (RLPList) RLP.decode2(stream).get(0);
                    List<byte[]> res = new ArrayList<>(list.size());

                    for (RLPElement aList : list) {
                        res.add(aList.getRLPData());
                    }
                    return res;
                }
            };

    private static final Serializer<List<AionBlock>, byte[]> BLOCK_LIST_RLP_SERIALIZER =
            new Serializer<>() {
                @Override
                public byte[] serialize(List<AionBlock> object) {
                    byte[][] infoList = new byte[object.size()][];
                    int i = 0;
                    for (AionBlock b : object) {
                        infoList[i] = b.getEncoded();
                        i++;
                    }
                    return RLP.encodeList(infoList);
                }

                @Override
                public List<AionBlock> deserialize(byte[] stream) {
                    RLPList list = (RLPList) RLP.decode2(stream).get(0);
                    List<AionBlock> res = new ArrayList<>(list.size());

                    for (RLPElement aList : list) {
                        res.add(new AionBlock(aList.getRLPData()));
                    }
                    return res;
                }
            };

    public Map<ByteArrayWrapper, List<AionBlock>> loadBlockRange(long level) {
        // get the queue for the given level
        List<byte[]> queueHashes = levelSource.get(ByteUtil.longToBytes(level));

        if (queueHashes == null) {
            return Collections.emptyMap();
        }

        // get all the blocks in the given queues
        List<AionBlock> list;
        Map<ByteArrayWrapper, List<AionBlock>> blocks = new HashMap<>();
        for (byte[] queue : queueHashes) {
            list = queueSource.get(queue);
            if (list != null) {
                blocks.put(ByteArrayWrapper.wrap(queue), list);
            }
        }

        return blocks;
    }

    public long nextBase(long current, long knownBest) {
        lock.writeLock().lock();

        try {
            if (LOG_SYNC.isDebugEnabled()) {
                LOG_SYNC.debug(statusToString());
            }

            long base;

            if (knownBest > maxStatus) {
                maxStatus = knownBest;
            }

            if (maxStatus == 0) {
                // optimistic jump forward
                base = current > maxRequest ? current : maxRequest;
                base += FORWARD_SKIP;
            } else if (current + LARGE_REQUEST_SIZE >= maxStatus) {
                // signal to switch back to / stay in NORMAL mode
                base = current;
            } else {
                base = current > maxRequest ? current : maxRequest;
                base += FORWARD_SKIP;

                // TODO: enhancement: special logic for status imports
            }

            if (LOG_SYNC.isDebugEnabled()) {
                LOG_SYNC.debug(
                        "min status = {}, max status = {}, max requested = {}, current = {}, returned base = {}",
                        minStatus,
                        maxStatus,
                        maxRequest,
                        current,
                        base);
            }

            // keep track of base
            if (base > maxRequest) {
                maxRequest = base;
            }

            // return new base
            return base;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Stores a single block in the pending block store for importing later when the chain reaches
     * the needed height and the parent block gets imported. Is used by the functionality receiving
     * status blocks.
     *
     * @implNote The status blocks received impact the functionality of the base value generation
     *     {@link #nextBase(long, long)} for requesting blocks ahead of import time.
     */
    public boolean addStatusBlock(AionBlock block) {

        // nothing to do with null parameter
        if (block == null) {
            return false;
        }

        lock.writeLock().lock();

        try {
            // skip if already stored
            if (!indexSource.get(block.getHash()).isPresent()) {

                // find parent queue hash
                Optional<byte[]> existingQueueHash = indexSource.get(block.getParentHash());
                byte[] currentQueueHash = null;
                List<AionBlock> currentQueue = null;

                // get existing queue if present
                if (existingQueueHash.isPresent()) {
                    // using parent queue hash
                    currentQueueHash = existingQueueHash.get();

                    // append block to queue
                    currentQueue = queueSource.get(currentQueueHash);
                } // do not add else here!

                // when no queue exists OR problem with existing queue
                if (currentQueue == null || currentQueue.size() == 0) {
                    // start new queue

                    // queue hash = the node hash
                    currentQueueHash = block.getHash();
                    currentQueue = new ArrayList<>();

                    // add (to) level
                    byte[] levelKey = ByteUtil.longToBytes(block.getNumber());
                    List<byte[]> levelData = levelSource.get(levelKey);

                    if (levelData == null) {
                        levelData = new ArrayList<>();
                    }

                    levelData.add(currentQueueHash);
                    levelSource.put(levelKey, levelData);
                }

                // NOTE: at this point the currentQueueHash was initialized
                // either with a previous hash OR the block hash

                // index block with queue hash
                indexSource.put(block.getHash(), currentQueueHash);

                // add element to queue
                currentQueue.add(block);
                queueSource.put(currentQueueHash, currentQueue);

                // update status tracking
                ByteArrayWrapper hash = ByteArrayWrapper.wrap(currentQueueHash);
                QueueInfo info = status.get(hash);
                if (info == null) {
                    if (Arrays.equals(currentQueueHash, block.getHash())) {
                        info =
                                new QueueInfo(
                                        currentQueueHash, block.getNumber(), block.getNumber());
                    } else {
                        info = new QueueInfo(currentQueueHash, block.getNumber());
                    }
                } else {
                    info.setLast(block.getNumber());
                }
                status.put(hash, info);

                if (minStatus > block.getNumber()) {
                    minStatus = block.getNumber();
                }

                if (maxStatus < block.getNumber()) {
                    maxStatus = block.getNumber();
                }

                // the block was added
                return true;
            } else {
                // block already stored
                return false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** @return the number of elements stored in the status map. */
    public int getStatusSize() {
        return status == null ? -1 : status.size();
    }

    /**
     * @param hash the identifier of a queue of blocks stored in the status map
     * @return the information for that queue if it exists, {@code null} otherwise.
     */
    public QueueInfo getStatusItem(byte[] hash) {
        if (hash == null) {
            return null;
        } else {
            return status.get(ByteArrayWrapper.wrap(hash));
        }
    }

    /**
     * Steps for storing the block data for later importing:
     *
     * <ol>
     *   <li>store block object in the <b>block</b> database
     *   <li>find or create queue hash and store it in the <b>index</b> database
     *   <li>add block hash to its queue in the <b>queue</b> database
     *   <li>if new queue, add it to the <b>level</b> database
     * </ol>
     */
    public int addBlockRange(List<AionBlock> blocks) {
        List<AionBlock> blockRange = new ArrayList<>(blocks);

        // nothing to do when 0 blocks given
        if (blockRange.size() == 0) {
            return 0;
        }

        lock.writeLock().lock();

        try {
            // first block determines the batch queue placement
            AionBlock first = blockRange.remove(0);

            int stored = addBlockRange(first, blockRange);

            // save data to disk
            indexSource.commitBatch();
            levelSource.flushBatch();
            queueSource.flushBatch();

            // the number of blocks added
            return stored;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private int addBlockRange(AionBlock first, List<AionBlock> blockRange) {

        // skip if already stored
        while (indexSource.get(first.getHash()).isPresent()) {
            if (blockRange.size() == 0) {
                return 0;
            } else {
                first = blockRange.remove(0);
            }
        }

        // the first block is not stored
        // start new queue with hash = first node hash
        byte[] currentQueueHash = first.getHash();
        List<AionBlock> currentQueue = new ArrayList<>();

        // add (to) level
        byte[] levelKey = ByteUtil.longToBytes(first.getNumber());
        List<byte[]> levelData = levelSource.get(levelKey);

        if (levelData == null) {
            levelData = new ArrayList<>();
        }

        levelData.add(currentQueueHash);
        levelSource.putToBatch(levelKey, levelData);

        // index block with queue hash
        indexSource.putToBatch(first.getHash(), currentQueueHash);
        int stored = 1;

        // add element to queue
        currentQueue.add(first);

        // keep track of parent to ensure correct range
        AionBlock parent = first;

        AionBlock current;
        // process rest of block range
        while (blockRange.size() > 0) {
            current = blockRange.remove(0);

            // check for issues with batch continuity and storage
            if (!Arrays.equals(current.getParentHash(), parent.getHash()) // continuity issue
                    || indexSource.get(current.getHash()).isPresent()) { // already stored

                // store separately
                stored += addBlockRange(current, blockRange);

                // done with loop
                break;
            }

            // index block to current queue
            indexSource.putToBatch(current.getHash(), currentQueueHash);

            // append block to queue
            currentQueue.add(current);
            stored++;

            // update parent
            parent = current;
        }

        // done with queue
        queueSource.putToBatch(currentQueueHash, currentQueue);

        // the number of blocks added
        return stored;
    }

    public void dropPendingQueues(
            long level,
            List<ByteArrayWrapper> queues,
            Map<ByteArrayWrapper, List<AionBlock>> blocks) {

        lock.writeLock().lock();

        try {
            // delete imported queues & blocks
            for (ByteArrayWrapper q : queues) {
                // load the queue from disk
                List<AionBlock> currentQ = queueSource.get(q.getData());

                // delete imported blocks
                for (AionBlock b : blocks.get(q)) {
                    // delete index
                    indexSource.putToBatch(b.getHash(), null);
                    currentQ.remove(b);
                }

                // delete queue
                queueSource.putToBatch(q.getData(), null);

                // the queue has been updated since the import read
                if (!currentQ.isEmpty()) {
                    // get first block in remaining queue
                    AionBlock first = currentQ.get(0);

                    // update queue hash to first remaining element
                    byte[] currentQueueHash = first.getHash();

                    // put in queue database
                    queueSource.putToBatch(currentQueueHash, currentQ);

                    // update block index
                    for (AionBlock b : currentQ) {
                        indexSource.putToBatch(b.getHash(), currentQueueHash);
                    }

                    // add (to) level
                    byte[] levelKey = ByteUtil.longToBytes(first.getNumber());
                    List<byte[]> levelData = levelSource.get(levelKey);

                    if (levelData == null) {
                        levelData = new ArrayList<>();
                    }

                    levelData.add(currentQueueHash);
                    levelSource.putToBatch(levelKey, levelData);
                }
            }

            // update level
            byte[] levelKey = ByteUtil.longToBytes(level);
            List<byte[]> levelData = levelSource.get(levelKey);

            if (levelData == null) {
                LOG.error(
                        "Corrupt data in PendingBlockStorage. Level (expected to exist) was not found.");
                // level already missing so nothing to do here
            } else {
                List<byte[]> updatedLevelData = new ArrayList<>();

                levelData.forEach(
                        qHash -> {
                            if (!queues.contains(ByteArrayWrapper.wrap(qHash))) {
                                // this queue was not imported
                                updatedLevelData.add(qHash);
                            }
                        });

                if (updatedLevelData.isEmpty()) {
                    // delete level
                    levelSource.putToBatch(levelKey, null);
                } else {
                    // update level
                    levelSource.putToBatch(levelKey, updatedLevelData);
                }
            }

            // push changed to disk
            indexSource.commitBatch();
            queueSource.flushBatch();
            levelSource.flushBatch();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void flush() {
        lock.writeLock().lock();
        try {
            levelSource.flush();
            queueSource.flush();
            if (!this.indexSource.isAutoCommitEnabled()) {
                this.indexSource.commit();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        lock.writeLock().lock();

        try {
            levelSource.close();
        } catch (Exception e) {
            LOG.error("Not able to close the pending blocks levels database:", e);
        } finally {
            try {
                queueSource.close();
            } catch (Exception e) {
                LOG.error("Not able to close the pending blocks queue database:", e);
            } finally {
                try {
                    indexSource.close();
                } catch (Exception e) {
                    LOG.error("Not able to close the pending blocks index database:", e);
                } finally {
                    lock.writeLock().unlock();
                }
            }
        }
    }

    private String statusToString() {
        StringBuilder sb = new StringBuilder("Current status queues:\n");
        for (QueueInfo i : status.values()) {
            sb.append(i.toString());
            sb.append('\n');
        }
        return sb.toString();
    }

    static class QueueInfo {

        private static final long UNKNOWN = -1;

        QueueInfo(byte[] _hash, long _last) {
            this.hash = _hash;
            this.first = UNKNOWN;
            this.last = _last;
        }

        QueueInfo(byte[] _hash, long _first, long _last) {
            this.hash = _hash;
            this.first = _first;
            this.last = _last;
        }

        public QueueInfo(byte[] data) {
            RLPList outerList = RLP.decode2(data);

            if (outerList.isEmpty()) {
                throw new IllegalArgumentException(
                        "The given data does not correspond to a QueueInfo object.");
            }

            RLPList list = (RLPList) outerList.get(0);
            this.hash = list.get(0).getRLPData();
            this.first = ByteUtil.byteArrayToLong(list.get(1).getRLPData());
            this.last = ByteUtil.byteArrayToLong(list.get(2).getRLPData());
        }

        // the hash identifying the queue
        private byte[] hash;
        // the number of the first block in the queue
        private long first;
        // the number of the last block in the queue
        private long last;

        public byte[] getHash() {
            return hash;
        }

        public long getFirst() {
            return first;
        }

        public long getLast() {
            return last;
        }

        void setLast(long last) {
            this.last = last;
        }

        public byte[] getEncoded() {
            byte[] hashElement = RLP.encodeElement(hash);
            byte[] firstElement = RLP.encodeElement(ByteUtil.longToBytes(first));
            byte[] lastElement = RLP.encodeElement(ByteUtil.longToBytes(last));
            return RLP.encodeList(hashElement, firstElement, lastElement);
        }

        @Override
        public String toString() {
            return "[ short hash: "
                    + Hex.toHexString(hash).substring(0, 6)
                    + " first: "
                    + first
                    + " last: "
                    + last
                    + " ]";
        }
    }
}