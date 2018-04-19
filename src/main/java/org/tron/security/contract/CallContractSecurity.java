package org.tron.security.contract;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.protos.Contract.ContractCallContract;

/*
 *  Used to check to make sure a security env.
 *  Created by Guo Yonggang @2018.04
 */

@Slf4j
public class CallContractSecurity extends ContractSecurity {

  public CallContractSecurity() {
    super();
  }

  @Override
  public boolean validateContract(Any contract) {
    try {
      ContractCallContract contractCallContract = contract.unpack(ContractCallContract.class);
      if (contractCallContract.getContractAddress() == null ||
              contractCallContract.getOwnerAddress() == null ||
              contractCallContract.getCallValue() == null ||
              contractCallContract.getData() == null) {
        return false;
      }

      return true;

    } catch (InvalidProtocolBufferException e) {
      return false;
    }
  }

}
