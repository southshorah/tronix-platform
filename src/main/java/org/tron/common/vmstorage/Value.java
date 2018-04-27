package org.tron.common.vmstorage;

import org.tron.core.capsule.*;

import javax.lang.model.element.TypeElement;
import java.util.Arrays;
import java.util.HashMap;

/**
 * @author Guo Yonggang
 * @since 27.04.2018
 */
public class Value {

    private Type type = null;
    private byte[] any = null;

    /**
     * @param any
     */
    public Value(byte[] any, Type type) {
        if (any != null && any.length > 0) {
            this.any = new byte[any.length];
            System.arraycopy(any, 0, this.any, 0, any.length);
            this.type = type;
        }
    }

    /**
     * @param any
     * @param type
     */
    public Value(byte[] any, int type) {
        if (any != null && any.length > 0) {
            this.any = new byte[any.length];
            System.arraycopy(any, 0, this.any, 0, any.length);
            this.type = new Type(type);
        }
    }

    /**
     * @param value
     */
    private Value(Value value) {
        if (value.getAny() != null && value.getAny().length > 0) {
            this.any = new byte[any.length];
            System.arraycopy(value.getAny(), 0, this.any, 0, value.getAny().length);
            this.type = value.getType().clone();
        }
    }

    /**
     * @return
     */
    public Value clone() {
        return new Value(this);
    }

    /**
     * @return
     */
    public byte[] getAny() {
        return any;
    }

    /**
     * @return
     */
    public Type getType() {
        return type;
    }

    /**
     * @param type
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * @param type
     */
    public void addType(Type type) {
        this.type.addType(type);
    }

    /**
     * @param type
     */
    public void addType(int type) {
        this.type.addType(type);
    }

    /**
     * @return
     */
    public AccountCapsule getAccount() {
        if (any == null) return null;
        return new AccountCapsule(any);
    }

    /**
     * @return
     */
    public TransactionCapsule getTransaction() {
        if (any == null) return null;
        return new TransactionCapsule(any);
    }

    /**
     * @return
     */
    public BlockCapsule getBlock() {
        if (any == null) return null;
        try {
            return new BlockCapsule(any);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return
     */
    public WitnessCapsule getWitness() {
        if (any == null) return null;
        return new WitnessCapsule(any);

    }

    /**
     * @return
     */
    public BytesCapsule getBlockIndex() {
        if (any == null) return null;
        return new BytesCapsule(any);
    }

    /**
     * @return
     */
    public CodeCapsule getCode() {
        if (any == null) return null;
        return new CodeCapsule(any);
    }

    /**
     * @return
     */
    public ContractCapsule getContractCapsule() {
        if (any == null) return null;
        return new ContractCapsule(any);
    }

    /**
     * @return
     */
    public StorageCapsule getStorageCapsule() {
        if (any == null) return null;
        return new StorageCapsule(any);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || obj.getClass() != getClass()) return false;

        Value V = (Value)obj;
        if (Arrays.equals(this.any, V.getAny())) return true;
        return false;
    }

    public static Value create(byte[] any, int type) {
        return new Value(any, type);
    }

    public static Value create(byte[] any, Type type) {
        return new Value(any, type);
    }

    public static Value create(byte[] any) {
        return new Value(any, Type.VALUE_TYPE_NORMAL);
    }
}
