package org.tron.common.vmstorage;

import org.tron.common.vm.DataWord;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.ContractCapsule;
import org.tron.core.capsule.StorageCapsule;
import org.tron.protos.Protocol;

/**
 * @author Guo Yonggang
 * @since 2018.04
 */
public interface Deposit extends org.tron.common.vmstorage.facade.Deposit{

    AccountCapsule createAccount(byte[] address, Protocol.AccountType type);

    AccountCapsule getAccount(byte[] address);

    void createContract(byte[] address, ContractCapsule contractCapsule);

    ContractCapsule getContract(byte[] address);

    void saveCode(byte[] codeHash, byte[] code);

    byte[] getCode(byte[] codeHash);

    byte[] getCodeHash(byte[] address);

    void addStorageValue(byte[] address, DataWord key, DataWord value);

    DataWord getStorageValue(byte[] address, DataWord key);

    StorageCapsule getStorage(byte[] address);

    long getBalance(byte[] address);

    long addBalance(byte[] address, long value);

    Deposit newDepositChild();

    Deposit newDepositNext();

    void flush();

    void commit();

    int getIndex();
}
