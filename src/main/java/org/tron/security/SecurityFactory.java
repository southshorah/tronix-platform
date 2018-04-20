package org.tron.security;

import com.google.protobuf.Any;
import lombok.extern.slf4j.Slf4j;
import org.tron.protos.Contract;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;
import org.tron.security.contract.CallContractSecurity;
import org.tron.security.contract.ContractSecurity;
import org.tron.security.contract.CreateContractSecurity;

import java.util.HashMap;
import java.util.Map;

/*
 *  Used to check to make sure a security env.
 *  Created by Guo Yonggang @2018.04
 */
@Slf4j
public class SecurityFactory {
  private boolean ON = true;

  private Map<ContractType, ContractSecurity> contractSecurityMap = new HashMap<>();

  private SecurityFactory() {
    init();
  }

  private void init() {
    contractSecurityMap.put(ContractType.ContractCreationContract,
            new CreateContractSecurity());
    contractSecurityMap.put(ContractType.ContractCallContract,
            new CallContractSecurity());
  }

  private ContractType getContractType(Any contract) {
    if (contract.is(Contract.ContractCreationContract.class)) {
      return ContractType.ContractCreationContract;
    }
    return null;
  }

  public boolean validateContract(Any contract) {
    if (!isON()) {
      return true;
    }

    if (contract == null) return false;

    ContractType contractType = getContractType(contract);
    ContractSecurity contractSecurity = contractSecurityMap.get(contractType);
    if (contractSecurity != null) {
      return contractSecurity.validateContract(contract);
    } else {
      return true;
    }
  }

  public void setON(boolean state) {
    this.ON = state;
  }

  public boolean isON() {
    return ON;
  }

  private static final SecurityFactory INSTANCE = new SecurityFactory();
  public static SecurityFactory getInstance() {
    return INSTANCE;
  }

}
