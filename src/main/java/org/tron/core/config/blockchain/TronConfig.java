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
package org.tron.core.config.blockchain;

import org.apache.commons.lang3.tuple.Pair;
import org.tron.common.utils.Utils;
import org.tron.common.vm.DataWord;
import org.tron.common.vm.GasCost;
import org.tron.common.vm.OpCode;
import org.tron.core.config.BlockchainConfig;
import org.tron.core.config.BlockchainNetConfig;
import org.tron.core.config.Constants;
import org.tron.core.db.Repository;
import org.tron.protos.Protocol;

import java.math.BigInteger;
import java.util.List;

/**
 * Created by Anton Nashatyrev on 14.10.2016.
 */
public class TronConfig implements BlockchainConfig, BlockchainNetConfig {
    private static final GasCost GAS_COST = new GasCost();

    protected BlockchainConfig parent;

    public TronConfig(BlockchainConfig parent) {
        this.parent = parent;
    }

    @Override
    public Constants getConstants() {
        return parent.getConstants();
    }

    @Override
    public long getTransactionCost(Protocol.Transaction tx) {
        return parent.getTransactionCost(tx);
    }

    @Override
    public GasCost getGasCost() {
        return GAS_COST;
    }

    @Override
    public BlockchainConfig getConfigForBlock(long blockNumber) {
        return this;
    }

    @Override
    public Constants getCommonConstants() {
        return getConstants();
    }
}
