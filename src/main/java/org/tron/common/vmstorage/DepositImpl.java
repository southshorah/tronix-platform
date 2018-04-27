package org.tron.common.vmstorage;

import com.google.protobuf.ByteString;;
import org.tron.common.vm.DataWord;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.ContractCapsule;
import org.tron.core.capsule.StorageCapsule;
import org.tron.core.db.*;
import org.tron.protos.Protocol;
import java.util.*;

/**
 * @author Guo Yonggang
 * @since 27.04.2018
 */
public class DepositImpl implements Deposit, org.tron.common.vmstorage.facade.Deposit {

    protected Manager dbManager;
    protected DepositImpl parent = null;
    protected DepositImpl prevDeposit = null;
    protected DepositImpl nextDeposit = null;
    protected int index = -1;

    protected HashMap<Key, Value> accounCache = new HashMap<>();
    protected HashMap<Key, Value> transactionCache = new HashMap<>();
    protected HashMap<Key, Value> blockCache = new HashMap<>();
    protected HashMap<Key, Value> witnessCache = new HashMap<>();
    protected HashMap<Key, Value> blockIndexCache = new HashMap<>();
    protected HashMap<Key, Value> codeCache = new HashMap<>();
    protected HashMap<Key, Value> contractCache = new HashMap<>();
    protected HashMap<Key, Value> storageCache = new HashMap<>();

    public DepositImpl(Manager dbManager, DepositImpl parent, DepositImpl prev) {
        init(dbManager, parent, prev);
    }

    protected void init(Manager dbManager, DepositImpl parent, DepositImpl prev) {
        this.dbManager = dbManager;
        this.parent = parent;
        this.prevDeposit = prev;
        this.nextDeposit = null;
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
    public int getIndex() {
        return index;
    }

    @Override
    public Deposit newDepositChild() {
        return new DepositImpl(this.dbManager, this, null);
    }

    @Override
    public Deposit newDepositNext() {
        return this.nextDeposit = new DepositImpl(this.dbManager, null, this);
    }

    @Override
    public synchronized AccountCapsule createAccount(byte[] address, Protocol.AccountType type) {
        Key key = new Key(address);
        AccountCapsule account = new AccountCapsule(ByteString.copyFrom(address), type);
        Value value = new Value(account.getData(), Type.VALUE_TYPE_CREATE);
        this.accounCache.put(key, value);
        return account;
    }

    @Override
    public synchronized AccountCapsule getAccount(byte[] address) {
        Key key = new Key(address);
        if (this.accounCache.containsKey(key)) {
            return this.accounCache.get(key).getAccount();
        }

        AccountCapsule accountCapsule;
        if (parent != null) {
            accountCapsule = parent.getAccount(address);
        } else {
            accountCapsule = this.getAccountStore().get(address);
        }

        if (accountCapsule != null) {
            Value value = Value.create(accountCapsule.getData());
            this.accounCache.put(key, value);
        }
        return accountCapsule;
    }

    @Override
    public synchronized void createContract(byte[] address, ContractCapsule contractCapsule) {
        Key key = Key.create(address);
        Value value = Value.create(contractCapsule.getData(), Type.VALUE_TYPE_CREATE);
        this.contractCache.put(key, value);
    }

    @Override
    public synchronized ContractCapsule getContract(byte[] address) {
        Key key = Key.create(address);
        if (this.contractCache.containsKey(key)) {
            return this.contractCache.get(key).getContractCapsule();
        }

        ContractCapsule contractCapsule = null;
        if (parent != null) {
            contractCapsule = parent.getContract(address);
        } else {
            contractCapsule = this.getContractStore().get(address);
        }

        if (contractCapsule != null) {
            Value value = Value.create(contractCapsule.getData());
            this.contractCache.put(key, value);
        }
        return contractCapsule;
    }

    @Override
    public synchronized void saveCode(byte[] codeHash, byte[] code) {
        Key key = Key.create(codeHash);
        Value value = Value.create(code, Type.VALUE_TYPE_CREATE);
        this.codeCache.put(key, value);
    }

    @Override
    public synchronized byte[] getCode(byte[] codeHash) {
        Key key = Key.create(codeHash);
        if (this.codeCache.containsKey(key)) {
            return this.codeCache.get(key).getCode().getData();
        }

        byte[] code = null;
        if (parent != null) {
            code = parent.getCode(codeHash);
        } else {
            code = this.dbManager.getCodeStore().get(codeHash).getData();
        }
        return code;
    }

    @Override
    public byte[] getCodeHash(byte[] address) {
        AccountCapsule accountCapsule = getAccount(address);
        return accountCapsule != null ? accountCapsule.getCodeHash() : null;
    }

    @Override
    public synchronized StorageCapsule getStorage(byte[] address) {
        Key key = Key.create(address);
        if (this.storageCache.containsKey(address)) {
            return this.storageCache.get(key).getStorageCapsule();
        }

        StorageCapsule storageCapsule = null;
        if (parent != null) {
            storageCapsule = parent.getStorage(address);
        } else {
            storageCapsule = this.getStorageStore().get(address);
            if (storageCapsule == null) {
                Protocol.StorageItem.Builder builder = Protocol.StorageItem.newBuilder();
                builder.setContractAddress(ByteString.copyFrom(address));

                Protocol.StorageItem storageItem = builder.build();
                storageCapsule = new StorageCapsule(storageItem);

            }
        }

        if (storageCapsule != null) {
            Value value = Value.create(storageCapsule.getData(), Type.VALUE_TYPE_CREATE);
            this.storageCache.put(key, value);
        }

        return storageCapsule;
    }

    @Override
    public synchronized void addStorageValue(byte[] address, DataWord key, DataWord value) {
        if (getAccount(address) == null) return;
        Key addressKey = Key.create(address);
        if (this.storageCache.containsKey(addressKey)) {
            StorageCapsule storageCapsule = this.storageCache.get(addressKey).getStorageCapsule();
            storageCapsule.put(key, value);
            return;
        }

        StorageCapsule storageCapsule =  getStorage(address);
        storageCapsule.put(key, value);
    }

    @Override
    public synchronized DataWord getStorageValue(byte[] address, DataWord key) {
        if (getAccount(address) == null) return null;
        Key addressKey = Key.create(address);
        if (this.storageCache.containsKey(addressKey)) {
            StorageCapsule storageCapsule = this.storageCache.get(addressKey).getStorageCapsule();
            return storageCapsule.get(key);
        }

        StorageCapsule storageCapsule = getStorage(address);
        if (storageCapsule != null) {
            return storageCapsule.get(key);
        }

        return null;
    }

    @Override
    public synchronized long getBalance(byte[] address) {
        AccountCapsule accountCapsule = getAccount(address);
        return accountCapsule == null ? 0L : accountCapsule.getBalance();
    }

    @Override
    public synchronized long addBalance(byte[] address, long value) {
        AccountCapsule accountCapsule = getAccount(address);
        if (accountCapsule == null) {
            return 0L;
        }
        accountCapsule.setBalance(accountCapsule.getBalance() + value);
        Key key = Key.create(address);
        Value V = Value.create(accountCapsule.getData(), Type.VALUE_TYPE_DIRTY);
        this.storageCache.put(key, V);
        return accountCapsule.getBalance();
    }

    @Override
    public synchronized void commit() {
        DepositImpl deposit = parent == null ? this : parent;
        if (parent == null) {
            // Commit data  to prev deposit
        } else {

        }


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
    public void flush() {
        throw new RuntimeException("Not supported");
    }
}
