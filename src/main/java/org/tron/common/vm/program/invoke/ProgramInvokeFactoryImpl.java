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
import org.tron.common.vm.DataWord;
import org.tron.common.vm.program.Program;
import org.tron.core.db.Repository;
import org.tron.core.capsule.ContractCapsule;
import org.tron.core.db.BlockStore;
import org.tron.protos.Contract;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.Transaction;

/**
 * @author Roman Mandeleil
 * @since 08.06.2014
 */
@Component("ProgramInvokeFactory")
public class ProgramInvokeFactoryImpl implements ProgramInvokeFactory {

    private static final Logger logger = LoggerFactory.getLogger("VM");

    // Invocation by the wire tx
    @Override
    public ProgramInvoke createProgramInvoke(Transaction tx, Block block, Repository repository,
                                             BlockStore blockStore) {

        Contract.ContractCallContract contractCallContract = ContractCapsule.getCallContractFromTransaction(tx);
        /***         ADDRESS op       ***/
        // YP: Get address of currently executing account.
        // byte[] address = tx.isContractCreation() ? tx.getContractAddress() : tx.getReceiveAddress();
        byte[] address = contractCallContract.getContractAddress().toByteArray();

        /***         ORIGIN op       ***/
        // YP: This is the sender of original transaction; it is never a contract.
        // byte[] origin = tx.getSender();
        byte[] origin = contractCallContract.getOwnerAddress().toByteArray();

        /***         CALLER op       ***/
        // YP: This is the address of the account that is directly responsible for this execution.
        //byte[] caller = tx.getSender();
        byte[] caller = contractCallContract.getOwnerAddress().toByteArray();

        /***         BALANCE op       ***/
        // byte[] balance = repository.getBalance(address).toByteArray();
        long balance = repository.getBalance(address);

        /***         GASPRICE op       ***/
        //byte[] gasPrice = tx.getGasPrice();

        /*** GAS op ***/
        //byte[] gas = tx.getGasLimit();

        /***        CALLVALUE op      ***/
        // byte[] callValue = nullToEmpty(tx.getValue());
        byte[] callValue = contractCallContract.getCallValue().toByteArray();

        /***     CALLDATALOAD  op   ***/
        /***     CALLDATACOPY  op   ***/
        /***     CALLDATASIZE  op   ***/
        // byte[] data = tx.isContractCreation() ? ByteUtil.EMPTY_BYTE_ARRAY : nullToEmpty(tx.getData());
        byte[] data = contractCallContract.getData().toByteArray();

        /***    PREVHASH  op  ***/
        // byte[] lastHash = block.getParentHash();
        byte[] lastHash = block.getBlockHeader().getRawDataOrBuilder().getParentHash().toByteArray();

        /***   COINBASE  op ***/
        // byte[] coinbase = block.getCoinbase();
        byte[] coinbase = block.getBlockHeader().getRawDataOrBuilder().getWitnessAddress().toByteArray();

        /*** TIMESTAMP  op  ***/
        // long timestamp = block.getTimestamp();
        long timestamp = block.getBlockHeader().getRawDataOrBuilder().getTimestamp();

        /*** NUMBER  op  ***/
        // long number = block.getNumber();
        long number = block.getBlockHeader().getRawDataOrBuilder().getNumber();

        /*** DIFFICULTY  op  ***/
        // byte[] difficulty = block.getDifficulty();

        /*** GASLIMIT op ***/
        // byte[] gaslimit = block.getGasLimit();

        /*
        if (logger.isInfoEnabled()) {
            logger.info("Top level call: \n" +
                            "address={}\n" +
                            "origin={}\n" +
                            "caller={}\n" +
                            "balance={}\n" +
                            "gasPrice={}\n" +
                            "gas={}\n" +
                            "callValue={}\n" +
                            "data={}\n" +
                            "lastHash={}\n" +
                            "coinbase={}\n" +
                            "timestamp={}\n" +
                            "blockNumber={}\n" +
                            "difficulty={}\n" +
                            "gaslimit={}\n",

                    Hex.toHexString(address),
                    Hex.toHexString(origin),
                    Hex.toHexString(caller),
                    ByteUtil.bytesToBigInteger(balance),
                    ByteUtil.bytesToBigInteger(gasPrice),
                    ByteUtil.bytesToBigInteger(gas),
                    ByteUtil.bytesToBigInteger(callValue),
                    Hex.toHexString(data),
                    Hex.toHexString(lastHash),
                    Hex.toHexString(coinbase),
                    timestamp,
                    number,
                    Hex.toHexString(difficulty),
                    gaslimit);
        }
        */

        return new ProgramInvokeImpl(address, origin, caller, balance, callValue, data,
                lastHash, coinbase, timestamp, number, repository, blockStore);
    }

    /**
     * This invocation created for contract call contract
     */
    @Override
    public ProgramInvoke createProgramInvoke(Program program, DataWord toAddress, DataWord callerAddress,
                                             DataWord inValue, DataWord inGas,
                                             long balanceInt, byte[] dataIn,
                                             Repository repository, BlockStore blockStore,
                                             boolean isStaticCall, boolean byTestingSuite) {

        DataWord address = toAddress;
        DataWord origin = program.getOriginAddress();
        DataWord caller = callerAddress;

        DataWord balance = new DataWord(balanceInt);
        DataWord gasPrice = program.getGasPrice();
        DataWord gas = inGas;
        DataWord callValue = inValue;

        byte[] data = dataIn;
        DataWord lastHash = program.getPrevHash();
        DataWord coinbase = program.getCoinbase();
        DataWord timestamp = program.getTimestamp();
        DataWord number = program.getNumber();
        DataWord difficulty = program.getDifficulty();
        DataWord gasLimit = program.getGasLimit();

        if (logger.isInfoEnabled()) {
            logger.info("Internal call: \n" +
                            "address={}\n" +
                            "origin={}\n" +
                            "caller={}\n" +
                            "balance={}\n" +
                            "gasPrice={}\n" +
                            "gas={}\n" +
                            "callValue={}\n" +
                            "data={}\n" +
                            "lastHash={}\n" +
                            "coinbase={}\n" +
                            "timestamp={}\n" +
                            "blockNumber={}\n" +
                            "difficulty={}\n" +
                            "gaslimit={}\n",
                    Hex.toHexString(address.getLast20Bytes()),
                    Hex.toHexString(origin.getLast20Bytes()),
                    Hex.toHexString(caller.getLast20Bytes()),
                    balance.toString(),
                    gasPrice.longValue(),
                    gas.longValue(),
                    Hex.toHexString(callValue.getNoLeadZeroesData()),
                    data == null ? "" : Hex.toHexString(data),
                    Hex.toHexString(lastHash.getData()),
                    Hex.toHexString(coinbase.getLast20Bytes()),
                    timestamp.longValue(),
                    number.longValue(),
                    Hex.toHexString(difficulty.getNoLeadZeroesData()),
                    gasLimit.bigIntValue());
        }

        return new ProgramInvokeImpl(address, origin, caller, balance, gasPrice, gas, callValue,
                data, lastHash, coinbase, timestamp, number, difficulty, gasLimit,
                repository, program.getCallDeep() + 1, blockStore, isStaticCall, byTestingSuite);
    }
}
