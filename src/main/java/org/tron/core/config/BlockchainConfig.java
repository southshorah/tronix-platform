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
package org.tron.core.config;

import org.tron.common.vm.DataWord;
import org.tron.common.vm.GasCost;
import org.tron.common.vm.OpCode;
import org.tron.common.vm.program.Program;
import org.tron.core.db.BlockStore;
import org.tron.core.db.Repository;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.BlockHeader;
import org.tron.protos.Protocol.Transaction;

import java.math.BigInteger;

//import org.ethereum.mine.MinerIfc;
//import org.ethereum.validator.BlockHeaderValidator;

/**
 * Describes constants and algorithms used for a specific blockchain at specific stage
 *
 * Created by Anton Nashatyrev on 25.02.2016.
 */
public interface BlockchainConfig {

    /**
     * Get blockchain constants
     */
    Constants getConstants();

    /**
     * Calculates transaction gas fee
     */
    long getTransactionCost(Transaction tx);

    /**
     * EVM operations costs
     */
    GasCost getGasCost();
}
