package org.aion.avm.shadow.java.math;

import org.aion.avm.arraywrapper.ByteArray;
import org.aion.avm.internal.IObject;
import org.aion.avm.shadow.java.lang.Comparable;
import org.aion.avm.shadow.java.lang.String;
import org.aion.avm.shadow.java.lang.Number;

public class BigInteger extends Number implements Comparable<BigInteger> {

    public BigInteger(ByteArray val, int off, int len) {
        v = new java.math.BigInteger(val.getUnderlying(), off, len);
    }

    public BigInteger(ByteArray val) {
        this(val, 0, val.length());
    }

    public BigInteger(int signum, ByteArray magnitude, int off, int len){
        v = new java.math.BigInteger(signum, magnitude.getUnderlying(), off, len);
    }

    public BigInteger(int signum, ByteArray magnitude){
        this(signum, magnitude, 0, magnitude.length());
    }

    public BigInteger(String val, int radix) {
        v = new java.math.BigInteger(val.getUnderlying(), radix);
    }

    public BigInteger(String val) {
        this(val, 10);
    }

    public BigInteger avm_nextProbablePrime(){
        return new BigInteger(this.v.nextProbablePrime());
    }

    public static BigInteger avm_valueOf(long val) {
        return new BigInteger(java.math.BigInteger.valueOf(val));
    }

    public static final BigInteger avm_ZERO = new BigInteger(java.math.BigInteger.ZERO);

    public static final BigInteger avm_ONE = new BigInteger(java.math.BigInteger.ONE);

    public static final BigInteger avm_TWO = new BigInteger(java.math.BigInteger.TWO);

    public static final BigInteger avm_TEN = new BigInteger(java.math.BigInteger.TEN);

    public BigInteger avm_add(BigInteger val) {
        return new BigInteger(v.add(val.v));
    }

    public BigInteger avm_subtract(BigInteger val) {
        return new BigInteger(v.subtract(val.v));
    }

    public BigInteger avm_multiply(BigInteger val) {
        return new BigInteger(v.multiply(val.v));
    }

    public BigInteger avm_divide(BigInteger val) {
        return new BigInteger(v.divide(val.v));
    }

    public BigInteger avm_remainder(BigInteger val) {
        return new BigInteger(v.remainder(val.v));
    }

    public BigInteger avm_pow(int exponent) {
        return new BigInteger(v.pow(exponent));
    }

    public BigInteger avm_sqrt() {
        return new BigInteger(v.sqrt());
    }

    public BigInteger avm_gcd(BigInteger val) {
        return new BigInteger(v.gcd(val.v));
    }

    public BigInteger avm_abs() {
        return new BigInteger(v.abs());
    }

    public BigInteger avm_negate() {
        return new BigInteger(v.negate());
    }

    public int avm_signum() {
        return v.signum();
    }

    public BigInteger avm_mod(BigInteger val) {
        return new BigInteger(v.mod(val.v));
    }

    public BigInteger avm_modPow(BigInteger exponent, BigInteger m) {
        return new BigInteger(v.modPow(exponent.v, m.v));
    }

    public BigInteger avm_modInverse(BigInteger val) {
        return new BigInteger(v.modInverse(val.v));
    }

    public BigInteger avm_shiftLeft(int n) {
        return new BigInteger(v.shiftLeft(n));
    }

    public BigInteger avm_shiftRight(int n) {
        return new BigInteger(v.shiftRight(n));
    }

    public BigInteger avm_and(BigInteger val) {
        return new BigInteger(v.and(val.v));
    }

    public BigInteger avm_or(BigInteger val) {
        return new BigInteger(v.or(val.v));
    }

    public BigInteger avm_xor(BigInteger val) {
        return new BigInteger(v.xor(val.v));
    }

    public BigInteger avm_not() {
        return new BigInteger(v.not());
    }

    public BigInteger avm_andNot(BigInteger val) {
        return new BigInteger(v.andNot(val.v));
    }

    public boolean avm_testBit(int n) {
        return v.testBit(n);
    }

    public BigInteger avm_setBit(int n) {
        return new BigInteger(v.setBit(n));
    }

    public BigInteger avm_clearBit(int n) {
        return new BigInteger(v.clearBit(n));
    }

    public BigInteger avm_flipBit(int n) {
        return new BigInteger(v.flipBit(n));
    }

    public int avm_getLowestSetBit() {
        return v.getLowestSetBit();
    }

    public int avm_bitLength() {
        return v.bitLength();
    }

    public int avm_bitCount() {
        return v.bitLength();
    }

    public int avm_compareTo(BigInteger val) {
        return v.compareTo(val.v);
    }

    public boolean avm_equals(IObject x) {
        if (x == this)
            return true;

        if (!(x instanceof BigInteger))
            return false;

        BigInteger xInt = (BigInteger) x;
        return v.equals(xInt.v);
    }

    public BigInteger avm_min(BigInteger val){
        return new BigInteger(v.min(val.v));
    }

    public BigInteger avm_max(BigInteger val){
        return new BigInteger(v.max(val.v));
    }

    public int avm_hashCode() {
        return v.hashCode();
    }

    public String avm_toString(int radix){
        return new String(v.toString(radix));
    }

    public String avm_toString(){
        return new String(v.toString());
    }

    public ByteArray avm_toByteArray() {
        return new ByteArray(v.toByteArray());
    }

    public int avm_intValue(){
        return v.intValue();
    }

    public long avm_longValue(){
        return v.longValue();
    }

    public float avm_floatValue(){
        return v.floatValue();
    }

    public double avm_doubleValue(){
        return v.doubleValue();
    }

    public long avm_longValueExact(){
        return v.longValueExact();
    }

    public int avm_intValueExact() {
        return v.intValueExact();
    }

    public short avm_shortValueExact() {
        return v.shortValueExact();
    }
    public byte avm_byteValueExact() {
        return v.byteValueExact();
    }



    //========================================================
    // Methods below are used by runtime and test code only!
    //========================================================

    private java.math.BigInteger v;


    public BigInteger(java.math.BigInteger u) {
        v = u;
    }

    public java.math.BigInteger getV() {
        return v;
    }

    //========================================================
    // Methods below are excluded from shadowing
    //========================================================

    //public BigInteger(int numBits, Random rnd)

    //public BigInteger(int bitLength, int certainty, Random rnd)

    //public static BigInteger probablePrime(int bitLength, Random rnd)

    //private static BigInteger smallPrime(int bitLength, int certainty, Random rnd)

    //private static BigInteger largePrime(int bitLength, int certainty, Random rnd)

    //public BigInteger[] divideAndRemainder(BigInteger val)

    //public BigInteger[] sqrtAndRemainder()

    //public boolean isProbablePrime(int certainty)



}