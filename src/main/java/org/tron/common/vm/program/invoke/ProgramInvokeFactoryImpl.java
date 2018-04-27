/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tron.common.vm.program.invoke;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;
import org.tron.common.utils.ByteUtil;
import org.tron.common.vm.DataWord;
import org.tron.common.vm.program.InternalTransaction;
import org.tron.common.vm.program.Program;
import org.tron.core.actuator.TransactionExecutor;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.db.Repository;
import org.tron.core.capsule.ContractCapsule;
import org.tron.core.db.BlockStore;
import org.tron.protos.Contract;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.Transaction;
import org.tron.common.vm.program.InternalTransaction.TrxType;
import org.tron.common.vm.program.InternalTransaction.ExecuterType;

import static org.tron.common.vm.program.InternalTransaction.TrxType.TRX_CONTRACT_CALL_TYPE;
import static org.tron.common.vm.program.InternalTransaction.TrxType.TRX_CONTRACT_CREATION_TYPE;
import static org.tron.common.vm.program.InternalTransaction.ExecuterType.*;

/**
 * @author Roman Mandeleil
 * @since 08.06.2014
 */
@Component("ProgramInvokeFactory")
public class ProgramInvokeFactoryImpl implements ProgramInvokeFactory {

    private static final Logger logger = LoggerFactory.getLogger("VM");

    // Invocation by the wire tx
    @Override
    public ProgramInvoke createProgramInvoke(TrxType trxType, ExecuterType executerType, Transaction tx, Block block, Repository repository,
                                             BlockStore blockStore) {
        byte[] contractAddress;
        byte[] ownerAddress;
        long balance;
        byte[] data;
        byte[] lastHash = null;
        byte[] coinbase = null;
        long timestamp = 0L;
        long number = -1L;

        if (trxType == TRX_CONTRACT_CREATION_TYPE) {
            Contract.ContractCreationContract contract = ContractCapsule.getCreationContractFromTransaction(tx);
            contractAddress = contract.getContractAddress().toByteArray();
            ownerAddress = contract.getOwnerAddress().toByteArray();
            balance = repository.getBalance(ownerAddress);
            data = ByteUtil.EMPTY_BYTE_ARRAY;

            switch (executerType) {
                case ET_NORMAL_TYPE:
                    lastHash = block.getBlockHeader().getRawDataOrBuilder().getParentHash().toByteArray();
                    coinbase = block.getBlockHeader().getRawDataOrBuilder().getWitnessAddress().toByteArray();
                    timestamp = block.getBlockHeader().getRawDataOrBuilder().getTimestamp();
                    number = block.getBlockHeader().getRawDataOrBuilder().getNumber();
                    break;
                case ET_PRE_TYPE:
                    break;
                default:
                    return null;
            }


            return new ProgramInvokeImpl(contractAddress, ownerAddress, ownerAddress, balance, null, data,
                    lastHash, coinbase, timestamp, number, repository, blockStore);

        } else if (trxType == TRX_CONTRACT_CALL_TYPE) {
            Contract.ContractCallContract contract = ContractCapsule.getCallContractFromTransaction(tx);
            /***         ADDRESS op       ***/
            // YP: Get address of currently executing account.
            // byte[] address = tx.isContractCreation() ? tx.getContractAddress() : tx.getReceiveAddress();
            byte[] address = contract.getContractAddress().toByteArray();

            /***         ORIGIN op       ***/
            // YP: This is the sender of original transaction; it is never a contract.
            // byte[] origin = tx.getSender();
            byte[] origin = contract.getOwnerAddress().toByteArray();

            /***         CALLER op       ***/
            // YP: This is the address of the account that is directly responsible for this execution.
            //byte[] caller = tx.getSender();
            byte[] caller = contract.getOwnerAddress().toByteArray();

            /***         BALANCE op       ***/
            // byte[] balance = repository.getBalance(address).toByteArray();
            balance = repository.getBalance(caller);

            /***        CALLVALUE op      ***/
            // byte[] callValue = nullToEmpty(tx.getValue());
            byte[] callValue = contract.getCallValue().toByteArray();

            /***     CALLDATALOAD  op   ***/
            /***     CALLDATACOPY  op   ***/
            /***     CALLDATASIZE  op   ***/
            // byte[] data = tx.isContractCreation() ? ByteUtil.EMPTY_BYTE_ARRAY : nullToEmpty(tx.getData());
            data = contract.getData().toByteArray();

            switch (executerType) {
                case ET_CONSTANT_TYPE:
                    break;
                case ET_PRE_TYPE:
                    break;
                case ET_NORMAL_TYPE:
                    /***    PREVHASH  op  ***/
                    lastHash = block.getBlockHeader().getRawDataOrBuilder().getParentHash().toByteArray();
                    /***   COINBASE  op ***/
                    coinbase = block.getBlockHeader().getRawDataOrBuilder().getWitnessAddress().toByteArray();
                    /*** TIMESTAMP  op  ***/
                    timestamp = block.getBlockHeader().getRawDataOrBuilder().getTimestamp();
                    /*** NUMBER  op  ***/
                    number = block.getBlockHeader().getRawDataOrBuilder().getNumber();
                    break;
                default:
                    break;
            }

            return new ProgramInvokeImpl(address, origin, caller, balance, callValue, data,
                    lastHash, coinbase, timestamp, number, repository, blockStore);
        } else {
            return null;
        }

    }

    /**
     * This invocation created for contract call contract
     */
    @Override
    public ProgramInvoke createProgramInvoke(Program program, DataWord toAddress, DataWord callerAddress,
                                             DataWord inValue, long balanceInt, byte[] dataIn,
                                             Repository repository, BlockStore blockStore,
                                             boolean isStaticCall, boolean byTestingSuite) {

        DataWord address = toAddress;
        DataWord origin = program.getOriginAddress();
        DataWord caller = callerAddress;
        DataWord balance = new DataWord(balanceInt);
        DataWord callValue = inValue;

        byte[] data = dataIn;
        DataWord lastHash = program.getPrevHash();
        DataWord coinbase = program.getCoinbase();
        DataWord timestamp = program.getTimestamp();
        DataWord number = program.getNumber();
        DataWord difficulty = program.getDifficulty();
        DataWord dropLimit = program.getDroplimit();

        return new ProgramInvokeImpl(address, origin, caller, balance, callValue,
                data, lastHash, coinbase, timestamp, number, difficulty,
                repository, program.getCallDeep() + 1, blockStore, isStaticCall, byTestingSuite);
    }
}
