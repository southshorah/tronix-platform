package org.tron.common.vmstorage;

import java.util.Arrays;

/**
 * @author Guo Yongang
 * @since 27.04.2018
 */
public class Key {

    private static int MAX_KEY_LENGTH = 32;
    private static int MIN_KEY_LENGTH = 1;

    /**
     * data could not be null
     */
    private byte[] data = new byte[32];

    /**
     *
     * @param data
     */
    public Key(byte[] data) {
        if (data != null && data.length != 0) {
            System.arraycopy(data, 0, this.data, 0, data.length);
        }
    }

    /**
     *
     * @param key
     */
    private Key(Key key) {
        System.arraycopy(key.getData(), 0, this.data, 0, this.data.length);
    }

    /**
     *
     * @return
     */
    public Key clone() {
        return new Key(this);
    }

    /**
     *
     * @return
     */
    public byte[] getData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Key key = (Key) o;
        if (Arrays.equals(key.getData(), this.data)) return true;
        return false;
    }

    @Override
    public int hashCode() {
        return data != null ? data.hashCode() : 0;
    }

    /**
     *
     * @param data
     * @return
     */
    public static Key create(byte[] data) {
        return new Key(data);
    }
}
