package org.tron.core.db;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Autowired;
import org.tron.common.crypto.Hash;
import org.tron.common.utils.ByteUtil;
import org.tron.common.utils.FastByteComparisons;
import org.tron.common.utils.Sha256Hash;
import org.tron.common.vm.DataWord;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.CodeCapsule;
import org.tron.core.capsule.ContractCapsule;
import org.tron.core.capsule.StorageCapsule;
import org.tron.core.config.SystemProperties;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Block;

import javax.annotation.Nullable;
import java.util.*;

/**
 * @author Guo Yonggang
 * @since 2018.04
 */
public class RepositoryImpl implements Repository, org.tron.core.facade.Repository {

    protected Manager dbManager;
    protected RepositoryImpl parent;
    protected RepositoryImpl child;

    private AccountStore accountStore;
    private TransactionStore transactionStore;
    private BlockStore blockStore;
    private UtxoStore utxoStore;
    private WitnessStore witnessStore;
    private AssetIssueStore assetIssueStore;
    private DynamicPropertiesStore dynamicPropertiesStore;
    private BlockIndexStore blockIndexStore;
    private CodeStore codeStore;
    private ContractStore contractStore;
    private StorageStore storageStore;

    /* Cache for current call contract */
    protected HashMap<byte[], Any> accounCache = new HashMap<>();
    protected HashMap<byte[], Any> codeCache = new HashMap<>();
    protected HashMap<byte[], Any> transactionCache = new HashMap<>();
    protected HashMap<byte[], Any> blockCache = new HashMap<>();
    protected HashMap<byte[], Any> witnessCache = new HashMap<>();
    protected HashMap<byte[], Any>


    @Autowired
    protected SystemProperties config = SystemProperties.getDefault();

    protected RepositoryImpl() {}

    public RepositoryImpl(Manager dbManager, RepositoryImpl parent) {
        init(dbManager, parent);

    }

    protected void init(Manager dbManager, RepositoryImpl parent) {
        this.dbManager = dbManager;
        this.parent = parent;
    }

    public Manager getDbManager() {
        return dbManager;
    }

    public BlockStore getBlockStore() {
        return dbManager.getBlockStore();
    }

    public ContractStore getContractStore() {
        return dbManager.getContractStore();
    }

    public AccountStore getAccountStore() {
        return dbManager.getAccountStore();
    }

    public CodeStore getCodeStore() {
        return dbManager.getCodeStore();
    }

    public StorageStore getStorageStore() {
        return dbManager.getStorageStore();
    }

    @Override
    public synchronized AccountCapsule createAccount(byte[] address, Protocol.AccountType type) {
        if (!this.getAccountStore().has(address)) {
            AccountCapsule account = new AccountCapsule(ByteString.copyFrom(address), type);
            this.getAccountStore().put(address, account);
            return account;
        } else {
            return this.getAccountStore().get(address);
        }
    }

    @Override
    public synchronized boolean isExist(byte[] addr) {
        return getAccountCapsule(addr) != null;
    }

    @Override
    public synchronized AccountCapsule getAccountCapsule(byte[] addr) {
        return this.getAccountStore().get(addr);
    }

    synchronized AccountCapsule getOrCreateAccountCapsule(byte[] addr, Protocol.AccountType type) {
        AccountCapsule ret = this.getAccountStore().get(addr);
        if (ret == null) {
            ret = createAccount(addr, type);
        }
        return ret;
    }

    @Override
    public synchronized void delete(byte[] addr) {
        this.getAccountStore().delete(addr);
        this.getStorageStore().delete(addr);
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
    public void saveContract(byte[] contractAddress, ContractCapsule contractCapsule) {
        this.dbManager.getContractStore().put(contractAddress, contractCapsule);
    }

    @Override
    public ContractCapsule getContract(byte[] contractAddress) {
        return null;
    }

    @Override
    public synchronized void saveCode(byte[] addr, byte[] code) {
        byte[] codeHash = Sha256Hash.hash(code);
        this.getCodeStore().put(codeHash, new CodeCapsule(code));
        AccountCapsule accountCapsule = getOrCreateAccountCapsule(addr, Protocol.AccountType.Contract);
        accountCapsule.setCodeHash(codeHash);
        this.getAccountStore().put(addr, accountCapsule);
    }

    @Override
    public synchronized byte[] getCode(byte[] addr) {
        byte[] codeHash = getCodeHash(addr);
        return  ByteUtil.isNullOrZeroArray(codeHash) ?
                ByteUtil.EMPTY_BYTE_ARRAY : getCodeStore().get(codeHash).getData();
    }

    @Override
    public byte[] getCodeHash(byte[] addr) {
        AccountCapsule accountCapsule = getAccountCapsule(addr);
        return accountCapsule != null ? accountCapsule.getCodeHash() : null;
    }

    @Override
    public synchronized void addStorageRow(byte[] addr, DataWord key, DataWord value) {
        getOrCreateAccountCapsule(addr, Protocol.AccountType.Contract);

        StorageCapsule storageCapsule = this.getStorageStore().get(addr);
        if (storageCapsule == null) {
            // create one
            Protocol.StorageItem.Builder builder = Protocol.StorageItem.newBuilder();
            builder.setContractAddress(ByteString.copyFrom(addr));

            Protocol.StorageItem storageItem = builder.build();
            storageCapsule = new StorageCapsule(storageItem);
        }

        storageCapsule.put(key, value);
        this.getStorageStore().put(addr, storageCapsule);
    }

    @Override
    public synchronized DataWord getStorageValue(byte[] addr, DataWord key) {
        AccountCapsule accountCapsule = getAccountCapsule(addr);
        return accountCapsule == null ? null : this.getStorageStore().get(addr).get(key);
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
        this.getAccountStore().put(addr, accountCapsule);
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
            this.getAccountStore().put(entry.getKey().getData(), entry.getValue());
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
