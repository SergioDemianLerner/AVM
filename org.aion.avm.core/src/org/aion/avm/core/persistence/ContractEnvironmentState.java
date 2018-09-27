package org.aion.avm.core.persistence;

import org.aion.avm.core.persistence.keyvalue.StorageKeys;
import org.aion.avm.internal.RuntimeAssertionError;


/**
 * Represents the part of the contract environment which must be persistent between invocations.
 */
public class ContractEnvironmentState {
    public static ContractEnvironmentState loadFromGraph(IObjectGraphStore graphStore) {
        byte[] rawData = graphStore.getStorage(StorageKeys.CONTRACT_ENVIRONMENT);
        RuntimeAssertionError.assertTrue(rawData.length == Integer.BYTES);
        StreamingPrimitiveCodec.Decoder decoder = new StreamingPrimitiveCodec.Decoder(rawData);
        int nextHashCode = decoder.decodeInt();
        return new ContractEnvironmentState(nextHashCode);
    }

    public static void saveToGraph(IObjectGraphStore graphStore, ContractEnvironmentState state) {
        byte[] bytes = new StreamingPrimitiveCodec.Encoder()
                .encodeInt(state.nextHashCode)
                .toBytes();
        graphStore.putStorage(StorageKeys.CONTRACT_ENVIRONMENT, bytes);
    }


    public final int nextHashCode;

    public ContractEnvironmentState(int nextHashCode) {
        this.nextHashCode = nextHashCode;
    }
}
