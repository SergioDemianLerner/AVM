package org.aion.avm.core;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import org.aion.avm.arraywrapper.ByteArray;
import org.aion.avm.core.classgeneration.CommonGenerators;
import org.aion.avm.core.classloading.AvmClassLoader;
import org.aion.avm.core.classloading.AvmSharedClassLoader;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.rt.BlockchainRuntime;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * As part of issue-77, we want to see what a more typical application can see, from inside our environment.
 * This test operates on BasicAppTestTarget to observe what we are doing, from the inside.
 * Eventually, this will change into a shape where we will use the standard AvmImpl to completely run this
 * life-cycle, but we want to prove that it works, in isolation, before changing its details to account for
 * this design (especially considering that the entry-point interface is likely temporary).
 */
public class BasicAppTest {
    private static AvmSharedClassLoader sharedClassLoader;

    @BeforeClass
    public static void setupClass() throws Exception {
        sharedClassLoader = new AvmSharedClassLoader(CommonGenerators.generateExceptionShadowsAndWrappers());
    }

    private Class<?> clazz;
    private Method decodeMethod;
    private BlockchainRuntime runtime;

    @Before
    public void setup() throws Exception {
        // NOTE:  This boiler-plate is pulled directly from HashCodeTest but will eventually be cut-over to using AvmImpl, differently.
        String className = BasicAppTestTarget.class.getName();
        byte[] raw = Helpers.loadRequiredResourceAsBytes(className.replaceAll("\\.", "/") + ".class");
        
        Forest<String, byte[]> classHierarchy = new HierarchyTreeBuilder()
                .addClass(className, "java.lang.Object", raw)
                .asMutableForest();
        
        AvmImpl avm = new AvmImpl(sharedClassLoader);
        Map<String, Integer> runtimeObjectSizes = avm.computeRuntimeObjectSizes();
        Map<String, Integer> allObjectSizes = avm.computeObjectSizes(classHierarchy, runtimeObjectSizes);
        Function<byte[], byte[]> transformer = (inputBytes) -> {
            return avm.transformClasses(Collections.singletonMap(className, inputBytes), classHierarchy, allObjectSizes).get(className);
        };
        Map<String, byte[]> classes = Helpers.mapIncludingHelperBytecode(Collections.singletonMap(className, transformer.apply(raw)));
        AvmClassLoader loader = new AvmClassLoader(sharedClassLoader, classes);
        this.clazz = loader.loadClass(className);
        // NOTE:  The user's side is pre-shadow so it uses "byte[]" whereas we look up "ByteArray", here.
        this.decodeMethod = this.clazz.getMethod("decode", BlockchainRuntime.class, ByteArray.class);
        Assert.assertEquals(loader, this.clazz.getClassLoader());
        
        this.runtime = new SimpleRuntime(new byte[0], new byte[0], 10000);
        Helpers.instantiateHelper(loader, this.runtime);
    }

    @Test
    public void testIdentity() throws Exception {
        ByteArray input = new ByteArray(new byte[] {BasicAppTestTarget.kMethodIdentity, 42, 13});
        ByteArray output = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input);
        // These should be the same instance.
        Assert.assertEquals(input, output);
    }
}
