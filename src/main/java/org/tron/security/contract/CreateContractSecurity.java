package org.tron.security.contract;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract.ContractCreationContract;

/*
 *  Used to check to make sure a security env.
 *  Created by Guo Yonggang @2018.04
 */

@Slf4j
public class CreateContractSecurity extends ContractSecurity {

  public CreateContractSecurity() {
    super();
  }

  @Override
  public boolean validateContract(Any contract) {
    try {
      ContractCreationContract contractCreationContract = contract.unpack(ContractCreationContract.class);
      if (contractCreationContract.getOwnerAddress() == null ||
              contractCreationContract.getBytecode() == null ||
              contractCreationContract.getAbi().getEntrysCount() == 0) {
        return false;
      }

      return true;

    } catch (InvalidProtocolBufferException e) {
      return false;
    }
  }

}
