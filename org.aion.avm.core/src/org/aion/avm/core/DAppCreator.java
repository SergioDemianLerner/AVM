package org.aion.avm.core;

import org.aion.avm.core.arraywrapping.ArrayWrappingClassAdapter;
import org.aion.avm.core.arraywrapping.ArrayWrappingClassAdapterRef;
import org.aion.avm.core.exceptionwrapping.ExceptionWrapping;
import org.aion.avm.core.instrument.ClassMetering;
import org.aion.avm.core.instrument.HeapMemoryCostCalculator;
import org.aion.avm.core.miscvisitors.ClinitStrippingVisitor;
import org.aion.avm.core.miscvisitors.ConstantVisitor;
import org.aion.avm.core.miscvisitors.InterfaceFieldMappingVisitor;
import org.aion.avm.core.miscvisitors.LoopingExceptionStrippingVisitor;
import org.aion.avm.core.miscvisitors.NamespaceMapper;
import org.aion.avm.core.miscvisitors.PreRenameClassAccessRules;
import org.aion.avm.core.miscvisitors.StrictFPVisitor;
import org.aion.avm.core.miscvisitors.UserClassMappingVisitor;
import org.aion.avm.core.persistence.AutomaticGraphVisitor;
import org.aion.avm.core.persistence.ContractEnvironmentState;
import org.aion.avm.core.persistence.IObjectGraphStore;
import org.aion.avm.core.persistence.LoadedDApp;
import org.aion.avm.core.persistence.ReflectionStructureCodec;
import org.aion.avm.core.persistence.keyvalue.KeyValueObjectGraph;
import org.aion.avm.core.rejection.RejectedClassException;
import org.aion.avm.core.rejection.RejectionClassVisitor;
import org.aion.avm.core.shadowing.ClassShadowing;
import org.aion.avm.core.shadowing.InvokedynamicShadower;
import org.aion.avm.core.stacktracking.StackWatcherClassAdapter;
import org.aion.avm.core.types.ClassInfo;
import org.aion.avm.core.types.Forest;
import org.aion.avm.core.types.GeneratedClassConsumer;
import org.aion.avm.core.types.ImmortalDappModule;
import org.aion.avm.core.types.RawDappModule;
import org.aion.avm.core.types.TransformedDappModule;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.core.verification.Verifier;
import org.aion.avm.internal.*;
import org.aion.kernel.*;
import org.aion.parallel.TransactionTask;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.TransactionContext;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class DAppCreator {
    private static final Logger logger = LoggerFactory.getLogger(DAppExecutor.class);

    /**
     * Validates all classes, including but not limited to:
     *
     * <ul>
     * <li>class format (hash, version, etc.)</li>
     * <li>no native method</li>
     * <li>no invalid opcode</li>
     * <li>package name does not start with <code>org.aion.avm</code></li>
     * <li>no access to any <code>org.aion.avm</code> packages but the <code>org.aion.avm.api</code> package</li>
     * <li>any assumptions that the class transformation has made</li>
     * <li>TODO: add more</li>
     * </ul>
     *
     * @param dapp the classes of DApp
     * @return true if the DApp is valid, otherwise false
     */
    private static boolean validateDapp(RawDappModule dapp) {

        // TODO: Rom, complete module validation

        return true;
    }

    /**
     * Returns the sizes of all the user-space classes
     *
     * @param classHierarchy     the class hierarchy
     * @return The look-up map of the sizes of user objects
     * Class name is in the JVM internal name format, see {@link org.aion.avm.core.util.Helpers#fulllyQualifiedNameToInternalName(String)}
     */
    public static Map<String, Integer> computeUserObjectSizes(Forest<String, ClassInfo> classHierarchy, Map<String, Integer> rootObjectSizes)
    {
        HeapMemoryCostCalculator objectSizeCalculator = new HeapMemoryCostCalculator();

        // compute the user object sizes
        objectSizeCalculator.calcClassesInstanceSize(classHierarchy, rootObjectSizes);

        // copy over the user object sizes
        Map<String, Integer> userObjectSizes = new HashMap<>();
        objectSizeCalculator.getClassHeapSizeMap().forEach((k, v) -> {
            if (!rootObjectSizes.containsKey(k)) {
                userObjectSizes.put(k, v);
            }
        });
        return userObjectSizes;
    }

    // NOTE:  This is only public because InvokedynamicTransformationTest calls it.
    public static Map<String, Integer> computeAllPostRenameObjectSizes(Forest<String, ClassInfo> forest) {
        Map<String, Integer> preRenameUserObjectSizes = computeUserObjectSizes(forest, NodeEnvironment.singleton.preRenameRuntimeObjectSizeMap);

        Map<String, Integer> postRenameObjectSizes = new HashMap<>(NodeEnvironment.singleton.postRenameRuntimeObjectSizeMap);
        preRenameUserObjectSizes.forEach((k, v) -> postRenameObjectSizes.put(PackageConstants.kUserSlashPrefix + k, v));
        return postRenameObjectSizes;
    }

    /**
     * Replaces the <code>java.base</code> package with the shadow implementation.
     * Note that this is public since some unit tests call it, directly.
     *
     * @param inputClasses The class of DApp (names specified in .-style)
     * @param preRenameClassHierarchy The pre-rename hierarchy of user-defined classes in the DApp (/-style).
     * @return the transformed classes and any generated classes (names specified in .-style)
     */
    public static Map<String, byte[]> transformClasses(Map<String, byte[]> inputClasses, Forest<String, ClassInfo> preRenameClassHierarchy) {
        // Before anything, pass the list of classes through the verifier.
        // (this will throw UncaughtException, on verification failure).
        Verifier.verifyUntrustedClasses(inputClasses);
        
        // Note:  preRenameUserDefinedClasses includes ONLY classes while preRenameUserClassAndInterfaceSet includes classes AND interfaces.
        Set<String> preRenameUserDefinedClasses = ClassWhiteList.extractDeclaredClasses(preRenameClassHierarchy);
        ParentPointers parentClassResolver = new ParentPointers(preRenameUserDefinedClasses, preRenameClassHierarchy);
        
        // We need to run our rejection filter and static rename pass.
        Map<String, byte[]> safeClasses = rejectionAndRenameInputClasses(inputClasses, preRenameUserDefinedClasses, parentClassResolver);
        
        // merge the generated classes and processed classes, assuming the package spaces do not conflict.
        Map<String, byte[]> processedClasses = new HashMap<>();
        // WARNING:  This dynamicHierarchyBuilder is both mutable and shared by TypeAwareClassWriter instances.
        HierarchyTreeBuilder dynamicHierarchyBuilder = new HierarchyTreeBuilder();
        // merge the generated classes and processed classes, assuming the package spaces do not conflict.
        // We also want to expose this type to the class writer so it can compute common superclasses.
        GeneratedClassConsumer generatedClassesSink = (superClassSlashName, classSlashName, bytecode) -> {
            // Note that the processed classes are expected to use .-style names.
            String classDotName = Helpers.internalNameToFulllyQualifiedName(classSlashName);
            processedClasses.put(classDotName, bytecode);
            String superClassDotName = Helpers.internalNameToFulllyQualifiedName(superClassSlashName);
            dynamicHierarchyBuilder.addClass(classDotName, superClassDotName, false, bytecode);
        };
        Map<String, Integer> postRenameObjectSizes = computeAllPostRenameObjectSizes(preRenameClassHierarchy);

        Map<String, byte[]> transformedClasses = new HashMap<>();
        for (String name : safeClasses.keySet()) {
            // Note that transformClasses requires that the input class names by the .-style names.
            RuntimeAssertionError.assertTrue(-1 == name.indexOf("/"));

            // We need to parse with EXPAND_FRAMES, since the StackWatcherClassAdapter uses a MethodNode to parse methods.
            // We also add SKIP_DEBUG since we aren't using debug data and skipping it removes extraneous labels which would otherwise
            // cause the BlockBuildingMethodVisitor to build lots of small blocks instead of a few big ones (each block incurs a Helper
            // static call, which is somewhat expensive - this is how we bill for energy).
            int parsingOptions = ClassReader.EXPAND_FRAMES | ClassReader.SKIP_DEBUG;
            byte[] bytecode = new ClassToolchain.Builder(safeClasses.get(name), parsingOptions)
                    .addNextVisitor(new ConstantVisitor())
                    .addNextVisitor(new ClassMetering(postRenameObjectSizes))
                    .addNextVisitor(new InvokedynamicShadower(PackageConstants.kShadowSlashPrefix))
                    .addNextVisitor(new ClassShadowing(PackageConstants.kShadowSlashPrefix))
                    .addNextVisitor(new StackWatcherClassAdapter())
                    .addNextVisitor(new ExceptionWrapping(parentClassResolver, generatedClassesSink))
                    .addNextVisitor(new AutomaticGraphVisitor())
                    .addNextVisitor(new StrictFPVisitor())
                    .addWriter(new TypeAwareClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, parentClassResolver, dynamicHierarchyBuilder))
                    .build()
                    .runAndGetBytecode();
            bytecode = new ClassToolchain.Builder(bytecode, parsingOptions)
                    .addNextVisitor(new ArrayWrappingClassAdapterRef())
                    .addNextVisitor(new ArrayWrappingClassAdapter())
                    .addWriter(new TypeAwareClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, parentClassResolver, dynamicHierarchyBuilder))
                    .build()
                    .runAndGetBytecode();
            transformedClasses.put(name, bytecode);
        }

        /*
         * Another pass to deal with static fields in interfaces.
         */
        GeneratedClassConsumer consumer = generatedClassesSink;
        Set<String> userInterfaceSlashNames = new HashSet<>();
        preRenameClassHierarchy.walkPreOrder(new Forest.VisitorAdapter<>() {
            public void onVisitRoot(Forest.Node<String, ClassInfo> root) {
                // TODO: we have any interface with fields?
            }
            public void onVisitNotRootNode(Forest.Node<String, ClassInfo> node) {
                if (node.getContent().isInterface()) {
                    userInterfaceSlashNames.add(Helpers.fulllyQualifiedNameToInternalName(PackageConstants.kUserDotPrefix + node.getId()));
                }
            }
        });
        String javaLangObjectSlashName = PackageConstants.kShadowSlashPrefix + "java/lang/Object";
        for (String name : transformedClasses.keySet()) {
            int parsingOptions = ClassReader.EXPAND_FRAMES | ClassReader.SKIP_DEBUG;
            byte[] bytecode = new ClassToolchain.Builder(transformedClasses.get(name), parsingOptions)
                    .addNextVisitor(new InterfaceFieldMappingVisitor(consumer, userInterfaceSlashNames, javaLangObjectSlashName))
                    .addWriter(new TypeAwareClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, parentClassResolver, dynamicHierarchyBuilder))
                    .build()
                    .runAndGetBytecode();
            processedClasses.put(name, bytecode);
        }

        return processedClasses;
    }

    public static void create(KernelInterface kernel, AvmInternal avm, TransactionTask task, TransactionContext ctx, AvmTransactionResult result) {
        // Expose the DApp outside the try so we can detach from it, when we exit.
        LoadedDApp dapp = null;
        try {
            // read dapp module
            Address dappAddress = ctx.getContractAddress();
            CodeAndArguments codeAndArguments = CodeAndArguments.decodeFromBytes(ctx.getTransactionData());
            if (codeAndArguments == null) {
                result.setResultCode(AvmTransactionResult.Code.FAILED_INVALID_DATA);
                result.setEnergyUsed(ctx.getTransaction().getEnergyLimit());
                return;
            }

            RawDappModule rawDapp = RawDappModule.readFromJar(codeAndArguments.code);
            if (rawDapp == null) {
                result.setResultCode(AvmTransactionResult.Code.FAILED_INVALID_DATA);
                result.setEnergyUsed(ctx.getTransaction().getEnergyLimit());
                return;
            }

            // validate dapp module
            if (!validateDapp(rawDapp)) {
                result.setResultCode(AvmTransactionResult.Code.FAILED_INVALID_DATA);
                result.setEnergyUsed(ctx.getTransaction().getEnergyLimit());
                return;
            }
            ClassHierarchyForest dappClassesForest = rawDapp.classHierarchyForest;

            // transform
            Map<String, byte[]> transformedClasses = transformClasses(rawDapp.classes, dappClassesForest);
            TransformedDappModule transformedDapp = TransformedDappModule.fromTransformedClasses(transformedClasses, rawDapp.mainClass);

            // We can now construct the abstraction of the loaded DApp which has the machinery for the rest of the initialization.
            IObjectGraphStore graphStore = new KeyValueObjectGraph(kernel,dappAddress);
            dapp = DAppLoader.fromTransformed(transformedDapp);
            
            // We start the nextHashCode at 1.
            int nextHashCode = 1;
            InstrumentationHelpers.pushNewStackFrame(dapp.runtimeSetup, dapp.loader, ctx.getTransaction().getEnergyLimit() - result.getEnergyUsed(), nextHashCode);
            // (we pass a null reentrant state since we haven't finished initializing yet - nobody can call into us).
            dapp.attachBlockchainRuntime(new BlockchainRuntimeImpl(kernel, avm, null, task, ctx, codeAndArguments.arguments, result, dapp.runtimeSetup));

            IInstrumentation threadInstrumentation = IInstrumentation.attachedThreadInstrumentation.get();
            threadInstrumentation.chargeEnergy(BillingRules.getDeploymentFee(rawDapp.numberOfClasses, rawDapp.bytecodeSize));

            // Create the immortal version of the transformed DApp code by stripping the <clinit>.
            Map<String, byte[]> immortalClasses = new HashMap<>();
            for (Map.Entry<String, byte[]> elt : transformedClasses.entrySet()) {
                String className = elt.getKey();
                byte[] transformedClass = elt.getValue();
                byte[] immortalClass = new ClassToolchain.Builder(transformedClass, 0)
                        .addNextVisitor(new ClinitStrippingVisitor())
                        .addWriter(new ClassWriter(0))
                        .build()
                        .runAndGetBytecode();
                immortalClasses.put(className, immortalClass);
            }
            ImmortalDappModule immortalDapp = ImmortalDappModule.fromImmortalClasses(immortalClasses, transformedDapp.mainClass);

            // store transformed dapp
            byte[] immortalDappJar = immortalDapp.createJar(dappAddress, ctx);
            kernel.putCode(dappAddress, immortalDappJar);

            // We want to bill the storage cost associated with the code, so use the jar size (since we don't save the initialization data).
            threadInstrumentation.chargeEnergy(BillingRules.getCodeStorageFee(rawDapp.bytecodeSize));
            InstrumentationBasedStorageFees feeProcessor = new InstrumentationBasedStorageFees(threadInstrumentation);

            // Force the classes in the dapp to initialize so that the <clinit> is run (since we already saved the version without).
            dapp.forceInitializeAllClasses();

            // Save back the state before we return.
            // -first, save out the classes
            ReflectionStructureCodec directGraphData = dapp.createCodecForInitialStore(feeProcessor, graphStore);
            dapp.saveClassStaticsToStorage(feeProcessor, directGraphData, graphStore);
            // -finally, save back the final state of the environment so we restore it on the next invocation.
            ContractEnvironmentState.saveToGraph(graphStore, new ContractEnvironmentState(threadInstrumentation.peekNextHashCode()));
            graphStore.flushWrites();

            // TODO: whether we should return the dapp address is subject to change
            result.setResultCode(AvmTransactionResult.Code.SUCCESS);
            result.setEnergyUsed(ctx.getTransaction().getEnergyLimit() - threadInstrumentation.energyLeft());
            result.setReturnData(dappAddress.toBytes());
            result.setStorageRootHash(graphStore.simpleHashCode());
        } catch (OutOfEnergyException e) {
            result.setResultCode(AvmTransactionResult.Code.FAILED_OUT_OF_ENERGY);
            result.setEnergyUsed(ctx.getTransaction().getEnergyLimit());

        } catch (OutOfStackException e) {
            result.setResultCode(AvmTransactionResult.Code.FAILED_OUT_OF_STACK);
            result.setEnergyUsed(ctx.getTransaction().getEnergyLimit());

        } catch (RevertException e) {
            result.setResultCode(AvmTransactionResult.Code.FAILED_REVERT);
            result.setEnergyUsed(ctx.getTransaction().getEnergyLimit());

        } catch (InvalidException e) {
            result.setResultCode(AvmTransactionResult.Code.FAILED_INVALID);
            result.setEnergyUsed(ctx.getTransaction().getEnergyLimit());

        } catch (UncaughtException e) {
            result.setResultCode(AvmTransactionResult.Code.FAILED_EXCEPTION);
            result.setEnergyUsed(ctx.getTransaction().getEnergyLimit());

            result.setUncaughtException(e.getCause());
            logger.debug("Uncaught exception", e.getCause());
        } catch (RejectedClassException e) {
            result.setResultCode(AvmTransactionResult.Code.FAILED_REJECTED);
            result.setEnergyUsed(ctx.getTransaction().getEnergyLimit());

        } catch (EarlyAbortException e) {
            result.setResultCode(AvmTransactionResult.Code.FAILED_ABORT);
            result.setEnergyUsed(0);

        } catch (AvmException e) {
            // We handle the generic AvmException as some failure within the contract.
            result.setResultCode(AvmTransactionResult.Code.FAILED);
            result.setEnergyUsed(ctx.getTransaction().getEnergyLimit());
        } catch (JvmError e) {
            // These are cases which we know we can't handle and have decided to handle by safely stopping the AVM instance so
            // re-throw this as the AvmImpl top-level loop will commute it into an asynchronous shutdown.
            throw e;
        } catch (Throwable t) {
            // There should be no other reachable kind of exception.  If we reached this point, something very strange is happening so log
            // this and bring us down.
            t.printStackTrace();
            System.exit(1);
        } finally {
            // Once we are done running this, no matter how it ended, we want to detach our thread from the DApp.
            if (null != dapp) {
                InstrumentationHelpers.popExistingStackFrame(dapp.runtimeSetup);
            }
        }
    }


    private static Map<String, byte[]> rejectionAndRenameInputClasses(Map<String, byte[]> inputClasses, Set<String> preRenameUserDefinedClasses, ParentPointers parentClassResolver) {
        Map<String, byte[]> safeClasses = new HashMap<>();
        Set<String> preRenameUserClassAndInterfaceSet = inputClasses.keySet();
        PreRenameClassAccessRules preRenameClassAccessRules = new PreRenameClassAccessRules(preRenameUserDefinedClasses, preRenameUserClassAndInterfaceSet);
        NamespaceMapper namespaceMapper = new NamespaceMapper(preRenameClassAccessRules);
        
        for (String name : inputClasses.keySet()) {
            // Note that transformClasses requires that the input class names by the .-style names.
            RuntimeAssertionError.assertTrue(-1 == name.indexOf("/"));
            
            int parsingOptions = ClassReader.SKIP_DEBUG;
            byte[] bytecode = new ClassToolchain.Builder(inputClasses.get(name), parsingOptions)
                    .addNextVisitor(new RejectionClassVisitor(preRenameClassAccessRules, namespaceMapper))
                    .addNextVisitor(new LoopingExceptionStrippingVisitor())
                    .addNextVisitor(new UserClassMappingVisitor(namespaceMapper))
                    // (note that we need to pass a bogus HierarchyTreeBuilder into the class writer - can be empty, but not null)
                    .addWriter(new TypeAwareClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, parentClassResolver, new HierarchyTreeBuilder()))
                    .build()
                    .runAndGetBytecode();
            String mappedName = PackageConstants.kUserDotPrefix + name;
            safeClasses.put(mappedName, bytecode);
        }
        return safeClasses;
    }
}
