package org.aion.avm.core;

import java.math.BigInteger;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.core.util.TestingHelper;
import org.aion.kernel.AvmAddress;
import org.aion.kernel.Block;
import org.aion.kernel.KernelInterfaceImpl;
import org.aion.kernel.Transaction;
import org.aion.kernel.TransactionContextImpl;
import org.aion.kernel.AvmTransactionResult;
import org.aion.avm.api.ABIEncoder;
import org.aion.avm.api.Address;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.vm.api.interfaces.VirtualMachine;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


/**
 * Tests how we handle AssertionError's special constructors.
 */
public class AssertionErrorIntegrationTest {
    private static final Block BLOCK = new Block(new byte[32], 1L, Helpers.randomBytes(Address.LENGTH), System.currentTimeMillis(), new byte[0]);
    private static final long ENERGY_LIMIT = 10_000_000L;
    private static final long ENERGY_PRICE = 1L;

    private KernelInterface kernel;
    private VirtualMachine avm;

    @Before
    public void setup() {
        this.kernel = new KernelInterfaceImpl();
        this.avm = CommonAvmFactory.buildAvmInstance(this.kernel);
    }

    @After
    public void tearDown() {
        this.avm.shutdown();
    }

    @Test
    public void testEmpty() throws Exception {
        Address dapp = installTestDApp(AssertionErrorIntegrationTestTarget.class);
        
        // Do the call.
        String result = callStaticString(dapp, "emptyError");
        Assert.assertEquals(null, result);
    }

    @Test
    public void testThrowable() throws Exception {
        Address dapp = installTestDApp(AssertionErrorIntegrationTestTarget.class);
        
        // Do the call.
        String result = callStaticString(dapp, "throwableError");
        Assert.assertEquals(null, result);
    }

    @Test
    public void testBool() throws Exception {
        Address dapp = installTestDApp(AssertionErrorIntegrationTestTarget.class);
        
        // Do the call.
        String result = callStaticString(dapp, "boolError", true);
        Assert.assertEquals("true", result);
    }

    @Test
    public void testChar() throws Exception {
        Address dapp = installTestDApp(AssertionErrorIntegrationTestTarget.class);
        
        // Do the call.
        String result = callStaticString(dapp, "charError", 'a');
        Assert.assertEquals("a", result);
    }

    @Test
    public void testInt() throws Exception {
        Address dapp = installTestDApp(AssertionErrorIntegrationTestTarget.class);
        
        // Do the call.
        String result = callStaticString(dapp, "intError", 5);
        Assert.assertEquals("5", result);
    }

    @Test
    public void testLong() throws Exception {
        Address dapp = installTestDApp(AssertionErrorIntegrationTestTarget.class);
        
        // Do the call.
        String result = callStaticString(dapp, "longError", 5L);
        Assert.assertEquals("5", result);
    }

    @Test
    public void testFloat() throws Exception {
        Address dapp = installTestDApp(AssertionErrorIntegrationTestTarget.class);
        
        // Do the call.
        String result = callStaticString(dapp, "floatError", 5.0f);
        Assert.assertEquals("5.0", result);
    }

    @Test
    public void testDouble() throws Exception {
        Address dapp = installTestDApp(AssertionErrorIntegrationTestTarget.class);
        
        // Do the call.
        String result = callStaticString(dapp, "doubleError", 5.0d);
        Assert.assertEquals("5.0", result);
    }

    @Test
    public void testNormal() throws Exception {
        Address dapp = installTestDApp(AssertionErrorIntegrationTestTarget.class);
        
        // Do the call.
        String result = callStaticString(dapp, "normalError", new String("test").getBytes());
        Assert.assertEquals("test", result);
    }


    private Address installTestDApp(Class<?> testClass) {
        byte[] jar = JarBuilder.buildJarForMainAndClasses(testClass);
        byte[] txData = new CodeAndArguments(jar, new byte[0]).encodeToBytes();
        
        // Deploy.
        Transaction create = Transaction.create(KernelInterfaceImpl.PREMINED_ADDRESS, this.kernel.getNonce(KernelInterfaceImpl.PREMINED_ADDRESS).longValue(), BigInteger.ZERO, txData, ENERGY_LIMIT, ENERGY_PRICE);
        TransactionResult createResult = this.avm.run(new TransactionContext[] {new TransactionContextImpl(create, BLOCK)})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, createResult.getResultCode());
        return TestingHelper.buildAddress(createResult.getReturnData());
    }

    private String callStaticString(Address dapp, String methodName, Object... arguments) {
        byte[] argData = ABIEncoder.encodeMethodArguments(methodName, arguments);
        Transaction call = Transaction.call(KernelInterfaceImpl.PREMINED_ADDRESS, AvmAddress.wrap(dapp.unwrap()), this.kernel.getNonce(KernelInterfaceImpl.PREMINED_ADDRESS).longValue(), BigInteger.ZERO, argData, ENERGY_LIMIT, ENERGY_PRICE);
        TransactionResult result = this.avm.run(new TransactionContext[] {new TransactionContextImpl(call, BLOCK)})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        byte[] utf8 = (byte[])TestingHelper.decodeResult(result);
        return (null != utf8)
                ? new String(utf8)
                : null;
    }
}
