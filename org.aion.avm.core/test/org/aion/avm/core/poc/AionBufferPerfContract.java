package org.aion.avm.core.poc;

import org.aion.avm.api.ABIDecoder;
import org.aion.avm.api.BlockchainRuntime;
import org.aion.avm.userlib.AionBuffer;

public class AionBufferPerfContract {
    public static final int NUM_ELEMENTS = 4_000;
    public static final int TRANSFER_SIZE = 1_000;
    private static final byte[] BYTES = new byte[TRANSFER_SIZE];
    private static AionBuffer targetNobytes, targetNoChars, targetNoShorts, targetNoInts,
        targetNoFloats, targetNoLongs, targetNoDoubles, targetHasBytes, targetHasChars,
        targetHasShorts, targetHasInts, targetHasFloats, targetHasLongs, targetHasDoubles,
        targetHasIntsCopy;
    private static AionBuffer[] targetNothingTransferred, targetFullForTransfer;

    static {
        targetNobytes = AionBuffer.allocate(NUM_ELEMENTS);
        targetNoChars = AionBuffer.allocate(NUM_ELEMENTS * 2);
        targetNoShorts = AionBuffer.allocate(NUM_ELEMENTS * 2);
        targetNoInts = AionBuffer.allocate(NUM_ELEMENTS * 4);
        targetNoFloats = AionBuffer.allocate(NUM_ELEMENTS * 4);
        targetNoLongs = AionBuffer.allocate(NUM_ELEMENTS * 8);
        targetNoDoubles = AionBuffer.allocate(NUM_ELEMENTS * 8);
        targetHasBytes = fillWithBytes(AionBuffer.allocate(NUM_ELEMENTS));
        targetHasChars = fillWithChars(AionBuffer.allocate(NUM_ELEMENTS * 2));
        targetHasShorts = fillWithShorts(AionBuffer.allocate(NUM_ELEMENTS * 2));
        targetHasInts = fillWithInts(AionBuffer.allocate(NUM_ELEMENTS * 4));
        targetHasFloats = fillWithFloats(AionBuffer.allocate(NUM_ELEMENTS * 4));
        targetHasLongs = fillWithLongs(AionBuffer.allocate(NUM_ELEMENTS * 8));
        targetHasDoubles = fillWithDoubles(AionBuffer.allocate(NUM_ELEMENTS * 8));
        targetHasIntsCopy = AionBuffer.wrap(targetHasInts.array());
        targetNothingTransferred = produceEmptyBuffers(NUM_ELEMENTS);
        targetFullForTransfer = produceFullBuffers(NUM_ELEMENTS);
    }

    public static byte[] main() {
        return ABIDecoder.decodeAndRunWithObject(new AionBufferPerfContract(), BlockchainRuntime.getData());
    }

    public static void callPutByte() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetNobytes.putByte((byte) i);
    }

    public static void callPutChar() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetNoChars.putChar((char) i);
    }

    public static void callPutShort() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetNoShorts.putShort((short) i);
    }

    public static void callPutInt() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetNoInts.putInt(i);
    }

    public static void callPutFloat() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetNoFloats.putFloat(i);
    }

    public static void callPutLong() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetNoLongs.putLong(i);
    }

    public static void callPutDouble() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetNoDoubles.putDouble(i);
    }

    public static void callTransferBytesToBuffer() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetNothingTransferred[i].put(BYTES);
    }

    public static void callGetByte() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetHasBytes.getByte();
    }

    public static void callGetChar() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetHasChars.getChar();
    }

    public static void callGetShort() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetHasShorts.getShort();
    }

    public static void callGetInt() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetHasInts.getInt();
    }

    public static void callGetFloat() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetHasFloats.getFloat();
    }

    public static void callGetLong() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetHasLongs.getLong();
    }

    public static void callGetDouble() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetHasDoubles.getDouble();
    }

    public static void callTransferBytesFromBuffer() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetFullForTransfer[i].get(BYTES);
    }

    public static void callEquals() {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            targetHasInts.equals(targetHasIntsCopy);
    }

    // <------------------------------------------------------------------------------------------->

    private static AionBuffer fill(AionBuffer buffer) {
        int space = buffer.capacity() - buffer.size();
        for (int i = 0; i < space; i++)
            buffer.putByte((byte) i);
        return buffer;
    }

    private static AionBuffer fillWithBytes(AionBuffer buffer) {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            buffer.putByte((byte) i);
        return buffer;
    }

    private static AionBuffer fillWithChars(AionBuffer buffer) {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            buffer.putChar((char) i);
        return buffer;
    }

    private static AionBuffer fillWithShorts(AionBuffer buffer) {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            buffer.putShort((short) i);
        return buffer;
    }

    private static AionBuffer fillWithInts(AionBuffer buffer) {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            buffer.putInt(i);
        return buffer;
    }

    private static AionBuffer fillWithFloats(AionBuffer buffer) {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            buffer.putFloat(i);
        return buffer;
    }

    private static AionBuffer fillWithLongs(AionBuffer buffer) {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            buffer.putLong(i);
        return buffer;
    }

    private static AionBuffer fillWithDoubles(AionBuffer buffer) {
        for (int i = 0; i < NUM_ELEMENTS; i++)
            buffer.putDouble(i);
        return buffer;
    }

    private static AionBuffer[] produceEmptyBuffers(int num) {
        AionBuffer[] buffers = new AionBuffer[num];
        for (int i = 0; i < num; i++)
            buffers[i] = AionBuffer.allocate(TRANSFER_SIZE);
        return buffers;
    }

    private static AionBuffer[] produceFullBuffers(int num) {
        AionBuffer[] buffers = new AionBuffer[num];
        for (int i = 0; i < num; i++)
            buffers[i] = fill(AionBuffer.allocate(TRANSFER_SIZE));
        return buffers;
    }

}
