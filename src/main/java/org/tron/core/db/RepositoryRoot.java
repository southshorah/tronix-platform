package org.tron.core.db;

public class RepositoryRoot extends RepositoryImpl {

    public RepositoryRoot(Manager dbManager, RepositoryImpl parent) {
        init(dbManager, parent);
    }

    @Override
    public synchronized void commit() {
        super.commit();

        //stateTrie.flush();
        //trieCache.flush();
    }

    @Override
    public synchronized byte[] getRoot() {
        //storageCache.flush();
        // accountStateCache.flush();

        //return stateTrie.getRootHash();
        return null;
    }

    @Override
    public synchronized void flush() {
        commit();
    }

    @Override
    public Repository getSnapshotTo(byte[] root) {
        //return new RepositoryRoot(stateDS, root);
        return null;
    }

    @Override
    public synchronized String dumpStateTrie() {
        //return ((TrieImpl) stateTrie).dumpTrie();
        return null;
    }

    @Override
    public synchronized void syncToRoot(byte[] root) {
        //stateTrie.setRoot(root);
    }
}
