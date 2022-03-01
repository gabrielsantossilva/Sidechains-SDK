package com.horizen.examples;

import com.horizen.block.SidechainBlock;
import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.state.ApplicationState;
import com.horizen.state.SidechainStateReader;
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter;
import com.horizen.transaction.BoxTransaction;
import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.util.Success;
import scala.util.Try;

import java.io.File;
import java.util.*;

public class DefaultApplicationState implements ApplicationState {

    // two distinct storages are used in order to test a version misalignment during startup and the recover logic
    private VersionedLevelDbStorageAdapter appStorage1;
    private VersionedLevelDbStorageAdapter appStorage2;

    public DefaultApplicationState(File storage1, File storage2) {
        appStorage1 = new VersionedLevelDbStorageAdapter(storage1);
        appStorage2 = new VersionedLevelDbStorageAdapter(storage2);
    }

    public Logger logger = LogManager.getLogger(com.horizen.examples.DefaultApplicationState.class);

    @Override
    public void validate(SidechainStateReader stateReader, SidechainBlock block) throws IllegalArgumentException {
        // do nothing always successful
    }

    @Override
    public void validate(SidechainStateReader stateReader, BoxTransaction<Proposition, Box<Proposition>> transaction) throws IllegalArgumentException {
        // do nothing always successful
    }

    @Override
    public Try<ApplicationState> onApplyChanges(SidechainStateReader stateReader, byte[] blockId, List<Box<Proposition>> newBoxes, List<byte[]> boxIdsToRemove) {
        ByteArrayWrapper version = new ByteArrayWrapper(blockId);

        // version can not be used as a key, reverse it just to save some dummy key/value data
        ByteArrayWrapper key   = new ByteArrayWrapper(BytesUtils.reverseBytes(blockId));
        ByteArrayWrapper value = new ByteArrayWrapper(1);

        List<Pair<ByteArrayWrapper, ByteArrayWrapper>> toUpdate = Arrays.asList(new Pair<>(key, value));

        appStorage1.update(version, toUpdate, new ArrayList<>());
        appStorage2.update(version, toUpdate, new ArrayList<>());

        logger.info("Sidechain application state updated with version: " + BytesUtils.toHexString(version.data()));

        return new Success<>(this);
    }

    @Override
    public Try<ApplicationState> onRollback(byte[] blockId) {

        appStorage1.rollback(new ByteArrayWrapper(blockId));
        appStorage2.rollback(new ByteArrayWrapper(blockId));
        logger.info(String.format("Application state has been rollback to version: '%s'", BytesUtils.toHexString(blockId)));

        return new Success<>(this);
    }

    @Override
    public List<byte[]> getStoragesVersionList() {
        List<byte[]> list = new ArrayList<>();
        byte[] nullVersion = new byte[32];

        ByteArrayWrapper ver1 = appStorage1.lastVersionID().orElse(new ByteArrayWrapper(nullVersion));
        list.add(ver1.data());

        ByteArrayWrapper ver2 = appStorage2.lastVersionID().orElse(new ByteArrayWrapper(nullVersion));
        list.add(ver2.data());

        logger.debug(String.format("Application state current versions: '%s', '%s'",
                        BytesUtils.toHexString(ver1.data()),
                        BytesUtils.toHexString(ver2.data()))
                );

        return list;
    }
}
