package org.aion.avm.core.testWallet;

import org.aion.avm.api.Address;
import org.aion.avm.api.BlockchainRuntime;
import org.aion.avm.userlib.AionList;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.AionSet;


/**
 * Note that the original implementation has a very "C++ feel", with the way that a lot of independent mappings are used
 * to represent different views on the data and the underlying array of Addresses is flat and reused, with a fixed limit.
 * In our implementation, we will use common Java collections to accomplish much of this (we might add a limit, later,
 * but the core implementation doesn't require it).
 */
public class Multiowned {
    /**
     * Calling a reflection routine which has an array in it is a problem with how our arraywrapping works.
     * This might not matter, in the future, but may be something we need to make easier to manage (maybe the arraywrappers have factories, or something).
     */
    public static Multiowned avoidArrayWrappingFactory(EventLogger logger, Address sender, Address owner1, Address owner2, int votesRequiredPerOperation) {
        return new Multiowned(logger, sender, new Address[] {owner1, owner2}, votesRequiredPerOperation);
    }

    private final EventLogger logger;
    // The owners.
    private final AionSet<Address> owners;
    private final AionList<Address> ownersByJoinOrder;
    // The operations which we currently know about.
    private final AionMap<Operation, PendingState> ongoingOperations;
    // The number of owners which must confirm the operation before it is run.
    private int numberRequired;

    public Multiowned(EventLogger logger, Address sender, Address[] requestedOwners, int votesRequiredPerOperation) {
        this.logger = logger;
        this.owners = new AionSet<>();
        this.ownersByJoinOrder = new AionList<>();
        this.owners.add(sender);
        this.ownersByJoinOrder.add(sender);
        for (Address owner : requestedOwners) {
            this.owners.add(owner);
            this.ownersByJoinOrder.add(owner);
        }
        this.ongoingOperations = new AionMap<>();
        this.numberRequired = votesRequiredPerOperation;
    }

    // PUBLIC INTERFACE
    public void revoke(BlockchainRuntime runtime) {
        Address sender = runtime.getSender();
        // Make sure that they are an owner.
        if (this.owners.contains(sender)) {
            // See if we know about this operation.
            Operation operation = Operation.fromMessage(runtime);
            PendingState state = this.ongoingOperations.get(operation);
            if (null != state) {
                // Remove them from the confirmed set.
                state.confirmedOwners.remove(sender);
                this.logger.revoke(sender, operation);
            }
        }
    }

    // PUBLIC INTERFACE
    public void changeOwner(BlockchainRuntime runtime, Address from, Address to) {
        // (modifier)
        onlyManyOwners(runtime.getSender(), Operation.fromMessage(runtime));
        
        // Make sure that the to isn't already an owner and the from is.
        if (!isOwner(to) && isOwner(from)) {
            // Remove the one owner and add the other.
            this.owners.remove(from);
            this.owners.add(to);
            this.ownersByJoinOrder.remove(from);
            this.ownersByJoinOrder.add(to);
            
            // Pending states will now be inconsistent so clear them.
            this.ongoingOperations.clear();
            this.logger.ownerChanged(from, to);
        }
    }

    // PUBLIC INTERFACE
    public void addOwner(BlockchainRuntime runtime, Address owner) {
        // (modifier)
        onlyManyOwners(runtime.getSender(), Operation.fromMessage(runtime));
        
        // Make sure that this owner isn't already in the set.
        if (!isOwner(owner)) {
            // Remove the one owner and add the other.
            this.owners.add(owner);
            this.ownersByJoinOrder.add(owner);
            
            // Pending states will now be inconsistent so clear them.
            this.ongoingOperations.clear();
            this.logger.ownerAdded(owner);
        }
    }

    // PUBLIC INTERFACE
    public void removeOwner(BlockchainRuntime runtime, Address owner) {
        // (modifier)
        onlyManyOwners(runtime.getSender(), Operation.fromMessage(runtime));
        
        // Make sure that this owner is  in the set.
        if (isOwner(owner)) {
            // Remove the one owner and add the other.
            this.owners.remove(owner);
            this.ownersByJoinOrder.remove(owner);
            
            // Pending states will now be inconsistent so clear them.
            this.ongoingOperations.clear();
            this.logger.ownerRemoved(owner);
            
            // (Note that it is possible that we have now removed so many owners that numberRequired no longer exist - the Solidity example didn't avoid this)
        }
    }

    // PUBLIC INTERFACE
    public void changeRequirement(BlockchainRuntime runtime, int newRequired) {
        // (modifier)
        onlyManyOwners(runtime.getSender(), Operation.fromMessage(runtime));
        
        // Change the requirement.
        this.numberRequired = newRequired;
        
        // Pending states will now be inconsistent so clear them.
        this.ongoingOperations.clear();
        this.logger.requirementChanged(newRequired);
    }

    // PUBLIC INTERFACE
    // Gets the 0-indexed owner, in order of when they became owners.
    // This is just supported because the Solidity example had it but I am not sure how it would be used.
    public Address getOwner(int ownerIndex) {
        Address result = null;
        
        if (ownerIndex < this.ownersByJoinOrder.size()) {
            result = this.ownersByJoinOrder.get(ownerIndex);
        }
        return result;
    }

    // This is just supported because the Solidity example had it but it wasn't marked "external" and had no callers.
    public boolean hasConfirmed(Operation operation, Address owner) {
        boolean didConfirm = false;
        
        // Make sure they are an owner.
        if (this.owners.contains(owner)) {
            // Check to see if this is a real operation.
            PendingState pending = this.ongoingOperations.get(operation);
            if (null != pending) {
                didConfirm = pending.confirmedOwners.contains(owner);
            }
        }
        return didConfirm;
    }


    // MODIFIER - public for composition
    public void onlyOwner(Address sender) {
        if (!isOwner(sender)) {
            // We want to bail out with a failed "require".
            throw new RequireFailedException();
        }
    }

    /**
     * This is called in the case where the activity requires consensus of some owners.
     * This will confirm the operation for the sender but only return if they were the last required
     * to confirm (also, erasing the PendingState record, in that case).  RequireFailedException is
     * thrown in other cases.
     * 
     * @param sender The current message sender.
     * @param operation The operation hash.
     */
    // MODIFIER - public for composition
    public void onlyManyOwners(Address sender, Operation operation) {
        boolean shouldRunRoutine = confirmAndCheck(sender, operation);
        if (!shouldRunRoutine) {
            // We want to bail out with a failed "require".
            throw new RequireFailedException();
        }
    }

    private boolean isOwner(Address address) {
        return this.owners.contains(address);
    }

    private boolean confirmAndCheck(Address sender, Operation operation) {
        boolean shouldRunRoutine = false;
        
        // They must be an owner.
        if (this.owners.contains(sender)) {
            // We lazily create these the first time they are requested so see if there is one.
            PendingState state = this.ongoingOperations.get(operation);
            if (null == state) {
                state = new PendingState();
                this.ongoingOperations.put(operation, state);
            }
            
            // Add us to the set of those who have confirmed.
            this.logger.confirmation(sender, operation);
            state.confirmedOwners.add(sender);
            
            // If we were the last required, return true and clean up.
            if (state.confirmedOwners.size() == this.numberRequired) {
                shouldRunRoutine = true;
                this.ongoingOperations.remove(operation);
            }
        }
        return shouldRunRoutine;
    }


    // (this is public just for easy referencing in the Deployer's loader logic).
    public static class PendingState {
        public final AionSet<Address> confirmedOwners = new AionSet<>();
    }
}
