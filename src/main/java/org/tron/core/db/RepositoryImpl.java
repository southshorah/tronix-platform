package org.tron.core.db;

import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Autowired;
import org.tron.common.utils.ByteUtil;
import org.tron.common.utils.FastByteComparisons;
import org.tron.common.utils.Sha256Hash;
import org.tron.common.vm.DataWord;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.CodeCapsule;
import org.tron.core.capsule.StorageCapsule;
import org.tron.core.config.SystemProperties;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Block;

import javax.annotation.Nullable;
import java.util.*;

public class RepositoryImpl implements Repository, org.tron.core.facade.Repository {

    protected RepositoryImpl parent;
    protected AccountStore accountCache;
    protected CodeStore codeCache;
    protected StorageStore storageCache;

    @Autowired
    protected SystemProperties config = SystemProperties.getDefault();

    protected RepositoryImpl() {}

    public RepositoryImpl(AccountStore accountStore, CodeStore codeStore,
                          StorageStore storageCache) {
        init(accountStore, codeStore, storageCache);
    }

    protected void init(AccountStore accountStore, CodeStore codeStore,
                        StorageStore storageCache) {
        this.accountCache = accountStore;
        this.codeCache = codeStore;
        this.storageCache = storageCache;
    }

    @Override
    public synchronized AccountCapsule createAccount(byte[] address, Protocol.AccountType type) {
        if (!this.accountCache.has(address)) {
            AccountCapsule account = new AccountCapsule(ByteString.copyFrom(address), type);
            this.accountCache.put(address, account);
            return account;
        } else {
            return this.accountCache.get(address);
        }
    }

    @Override
    public synchronized boolean isExist(byte[] addr) {
        return getAccountCapsule(addr) != null;
    }

    @Override
    public synchronized AccountCapsule getAccountCapsule(byte[] addr) {
        return this.accountCache.get(addr);
    }

    synchronized AccountCapsule getOrCreateAccountCapsule(byte[] addr, Protocol.AccountType type) {
        AccountCapsule ret = this.accountCache.get(addr);
        if (ret == null) {
            ret = createAccount(addr, type);
        }
        return ret;
    }

    @Override
    public synchronized void delete(byte[] addr) {
        this.accountCache.delete(addr);
        storageCache.delete(addr);
    }

    @Override
    public synchronized ContractDetails getContractDetails(byte[] addr) {
        return new ContractDetailsImpl(addr);
    }

    @Override
    public synchronized boolean hasContractDetails(byte[] addr) {
        return getContractDetails(addr) != null;
    }

    @Override
    public synchronized void saveCode(byte[] addr, byte[] code) {
        byte[] codeHash = Sha256Hash.hash(code);
        codeCache.put(codeHash, new CodeCapsule(code));
        AccountCapsule accountCapsule = getOrCreateAccountCapsule(addr, Protocol.AccountType.Contract);
        accountCapsule.setCodeHash(codeHash);
        this.accountCache.put(addr, accountCapsule);
    }

    @Override
    public synchronized byte[] getCode(byte[] addr) {
        byte[] codeHash = getCodeHash(addr);
        //return FastByteComparisons.equal(codeHash, HashUtil.EMPTY_DATA_HASH) ?
        //        ByteUtil.EMPTY_BYTE_ARRAY : codeCache.get(codeHash).getData();
        return  codeCache.get(codeHash).getData();
    }

    @Override
    public byte[] getCodeHash(byte[] addr) {
        AccountCapsule accountCapsule = getAccountCapsule(addr);
        return accountCapsule != null ? accountCapsule.getCodeHash() : null;
    }

    @Override
    public synchronized void addStorageRow(byte[] addr, DataWord key, DataWord value) {
        getOrCreateAccountCapsule(addr, Protocol.AccountType.Contract);

        StorageCapsule storageCapsule = storageCache.get(addr);
        storageCapsule.put(key, value);
    }

    @Override
    public synchronized DataWord getStorageValue(byte[] addr, DataWord key) {
        AccountCapsule accountCapsule = getAccountCapsule(addr);
        return accountCapsule == null ? null : storageCache.get(addr).get(key);
    }

    @Override
    public synchronized long getBalance(byte[] addr) {
        AccountCapsule accountCapsule = getAccountCapsule(addr);
        return accountCapsule == null ? 0L : accountCapsule.getBalance();
    }

    @Override
    public synchronized long addBalance(byte[] addr, long value) {
        AccountCapsule accountCapsule = getAccountCapsule(addr);
        if (accountCapsule == null) {
            return 0L;
        }
        accountCapsule.setBalance(accountCapsule.getBalance() + value);
        this.accountCache.put(addr, accountCapsule);
        return accountCapsule.getBalance();
    }

    @Override
    public synchronized Repository getSnapshotTo(byte[] root) {
        return parent.getSnapshotTo(root);
    }

    @Override
    public synchronized void commit() {
        Repository parentSync = parent == null ? this : parent;
        // need to synchronize on parent since between different caches flush
        // the parent repo would not be in consistent state
        // when no parent just take this instance as a mock
        /*
        synchronized (parentSync) {
            storageCache.flush();
            codeCache.flush();
            accountStateCache.flush();
        }
        */
    }

    @Override
    public synchronized void rollback() {
        // nothing to do, will be GCed
    }

    @Override
    public byte[] getRoot() {
        throw new RuntimeException("Not supported");
    }

    public synchronized String getTrieDump() {
        return dumpStateTrie();
    }

    public String dumpStateTrie() {
        throw new RuntimeException("Not supported");
    }

    class ContractDetailsImpl implements ContractDetails {
        private byte[] address;

        public ContractDetailsImpl(byte[] address) {
            this.address = address;
        }

        @Override
        public void put(DataWord key, DataWord value) {
            RepositoryImpl.this.addStorageRow(address, key, value);
        }

        @Override
        public DataWord get(DataWord key) {
            return RepositoryImpl.this.getStorageValue(address, key);
        }

        @Override
        public byte[] getCode() {
            return RepositoryImpl.this.getCode(address);
        }

        @Override
        public byte[] getCode(byte[] codeHash) {
            throw new RuntimeException("Not supported");
        }

        @Override
        public void setCode(byte[] code) {
            RepositoryImpl.this.saveCode(address, code);
        }

        @Override
        public byte[] getStorageHash() {
            throw new RuntimeException("Not supported");
        }

        @Override
        public void decode(byte[] rlpCode) {
            throw new RuntimeException("Not supported");
        }

        @Override
        public void setDirty(boolean dirty) {
            throw new RuntimeException("Not supported");
        }

        @Override
        public void setDeleted(boolean deleted) {
            RepositoryImpl.this.delete(address);
        }

        @Override
        public boolean isDirty() {
            throw new RuntimeException("Not supported");
        }

        @Override
        public boolean isDeleted() {
            throw new RuntimeException("Not supported");
        }

        @Override
        public byte[] getEncoded() {
            throw new RuntimeException("Not supported");
        }

        @Override
        public int getStorageSize() {
            throw new RuntimeException("Not supported");
        }

        @Override
        public Set<DataWord> getStorageKeys() {
            throw new RuntimeException("Not supported");
        }

        @Override
        public Map<DataWord, DataWord> getStorage(@Nullable Collection<DataWord> keys) {
            throw new RuntimeException("Not supported");
        }

        @Override
        public Map<DataWord, DataWord> getStorage() {
            throw new RuntimeException("Not supported");
        }

        @Override
        public void setStorage(List<DataWord> storageKeys, List<DataWord> storageValues) {
            throw new RuntimeException("Not supported");
        }

        @Override
        public void setStorage(Map<DataWord, DataWord> storage) {
            throw new RuntimeException("Not supported");
        }

        @Override
        public byte[] getAddress() {
            return address;
        }

        @Override
        public void setAddress(byte[] address) {
            throw new RuntimeException("Not supported");
        }

        @Override
        public ContractDetails clone() {
            throw new RuntimeException("Not supported");
        }

        @Override
        public void syncStorage() {
            throw new RuntimeException("Not supported");
        }

        @Override
        public ContractDetails getSnapshotTo(byte[] hash) {
            throw new RuntimeException("Not supported");
        }
    }


    @Override
    public Set<byte[]> getAccountsKeys() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void dumpState(Block block, long gasUsed, int txNumber, byte[] txHash) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void flush() {
        throw new RuntimeException("Not supported");
    }


    @Override
    public void flushNoReconnect() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void syncToRoot(byte[] root) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean isClosed() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void close() {
    }

    @Override
    public void reset() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public int getStorageSize(byte[] addr) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public Set<DataWord> getStorageKeys(byte[] addr) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public Map<DataWord, DataWord> getStorage(byte[] addr, @Nullable Collection<DataWord> keys) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void updateBatch(HashMap<ByteArrayWrapper, AccountCapsule> accountStates, HashMap<ByteArrayWrapper, ContractDetails> contractDetailes) {
        for (Map.Entry<ByteArrayWrapper, AccountCapsule> entry : accountStates.entrySet()) {
            this.accountCache.put(entry.getKey().getData(), entry.getValue());
        }
        for (Map.Entry<ByteArrayWrapper, ContractDetails> entry : contractDetailes.entrySet()) {
            ContractDetails details = getContractDetails(entry.getKey().getData());
            for (DataWord key : entry.getValue().getStorageKeys()) {
                details.put(key, entry.getValue().get(key));
            }
            byte[] code = entry.getValue().getCode();
            if (code != null && code.length > 0) {
                details.setCode(code);
            }
        }
    }

    @Override
    public void loadAccount(byte[] addr, HashMap<ByteArrayWrapper, AccountCapsule> cacheAccounts, HashMap<ByteArrayWrapper, ContractDetails> cacheDetails) {
        throw new RuntimeException("Not supported");
    }

}
