package be.uantwerpen.scicraft.gui;

import be.uantwerpen.scicraft.Scicraft;
import be.uantwerpen.scicraft.block.entity.LewisBlockEntity;
import be.uantwerpen.scicraft.inventory.slot.LewisCraftingResultSlot;
import be.uantwerpen.scicraft.inventory.slot.LewisErlenmeyerSlot;
import be.uantwerpen.scicraft.inventory.slot.LewisGridSlot;
import be.uantwerpen.scicraft.inventory.slot.LewisInputSlot;
import be.uantwerpen.scicraft.item.AtomItem;
import be.uantwerpen.scicraft.item.Items;
import be.uantwerpen.scicraft.lewisrecipes.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LewisBlockScreenHandler extends ScreenHandler {

    private final ScreenHandlerContext context;
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private Molecule currentMolecule;

    public BlockPos pos;
    private final LewisCraftingResultSlot outputSlot;
    private final LewisErlenmeyerSlot erlenmeyerSlot;
    private boolean locked;
    private int slotsready;

    /**
     * This constructor gets called on the client when the server wants it to open the screenHandler<br>
     * The client will call the other constructor with an empty Inventory and the screenHandler will automatically
     * sync this empty inventory with the inventory on the server.
     *
     * @param syncId          ID used to sync client and server handlers
     * @param playerInventory Player's inventory to sync with screen's inventory slots
     */
    public LewisBlockScreenHandler(int syncId, PlayerInventory playerInventory) {
        //this(syncId, playerInventory, new SimpleInventory(35));
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY, new SimpleInventory(36), new ArrayPropertyDelegate(1));
        this.onContentChanged(inventory);
    }

    public LewisBlockScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory);
        pos = buf.readBlockPos();
    }

    /**
     * This constructor gets called from the BlockEntity on the server without calling the other constructor first,
     * the server knows the inventory of the container and can therefore directly provide it as an argument.
     * This inventory will then be synced to the client.
     *
     * @param syncId           ID used to sync client and server handlers
     * @param playerInventory  Player's inventory to sync with screen's inventory slots
     * @param context          The BlockEntity's context for running functions on its world and position
     * @param inventory        The BlockEntity's inventory
     * @param propertyDelegate PropertyDelegate is used to sync data across server and client side handlers
     */
    public LewisBlockScreenHandler(int syncId, @NotNull PlayerInventory playerInventory, ScreenHandlerContext context, Inventory inventory, PropertyDelegate propertyDelegate) {
        super(Screens.LEWIS_SCREEN_HANDLER, syncId);
        checkSize(inventory, 36);
        this.context = context;
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        pos = context.get((w,p) -> p).get();

        this.currentMolecule = null;

        this.addProperties(propertyDelegate);
        //some inventories do custom logic when a player opens it.
        inventory.onOpen(playerInventory.player);

        //This will place the slot in the correct locations for a 3x3 Grid. The slots exist on both server and client!
        //This will not render the background of the slots however, this is the Screens job
        int m;
        int l;

        // offset
        int o = 11 - 29;

        // Lewis Crafting Table Inventory (5x5 grid)
        for (m = 0; m < 5; ++m) {
            for (l = 0; l < 5; ++l) {
                this.addSlot(new LewisGridSlot(inventory, l + m * 5, 8 + l * 18, m * 18 - o) {
                    @Override
                    public boolean isLocked() {
                        return LewisBlockScreenHandler.this.locked;
                    }
                });
            }
        }
        // Lewis Crafting Table Inventory (9 input slots)
        for (m = 0; m < 9; ++m) {
            this.addSlot(new LewisInputSlot(inventory, m + 25, 8 + m * 18, 5 * 18 - o + 5));
        }

        // Lewis Crafting Table Inventory (1 output slot)
        this.outputSlot = (LewisCraftingResultSlot) this.addSlot(new LewisCraftingResultSlot(inventory, 34, 8 + 7 * 18, 2 * 18 - o));

        // Lewis Crafting Table Inventory (1 slot for erlenmeyer)
        this.erlenmeyerSlot = (LewisErlenmeyerSlot) this.addSlot(new LewisErlenmeyerSlot(inventory, 35, 8 + 7 * 18, 2 * 18 - o + 36));

        //The player inventory (3x9 slots)
        for (m = 0; m < 3; ++m) {
            for (l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + m * 9 + 9, 8 + l * 18, 122 + m * 18 - o + 5));
            }
        }
        //The player Hotbar (9 slots)
        for (m = 0; m < 9; ++m) {
            this.addSlot(new Slot(playerInventory, m, 8 + m * 18, 180 - o + 5));
        }

        this.addListener(new ScreenHandlerListener() {
            @Override
            public void onSlotUpdate(ScreenHandler handler, int slotId, ItemStack stack) {
                handler.sendContentUpdates();
                handler.onContentChanged(inventory);
                handler.sendContentUpdates();
            }

            @Override
            public void onPropertyUpdate(ScreenHandler handler, int property, int value) {
                //maybe use later
                handler.updateToClient();
            }
        });

        onContentChanged(inventory);
    }

    /**
     * Supplies {@link LewisScreen} with an instance of the BlockEntity's Inventory
     *
     * @return Returns the BlockEntity's Inventory
     */
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Check if a player has access to the inventory and thus the BlockEntity
     *
     * @param player Player to check access for
     * @return Returns true if the player has access
     */
    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    // Shift + Player Inv Slot

    /**
     * Gets called when a player shift clicks while the screen is open
     *
     * @param player  Player that's shift clicking
     * @param invSlot SlotId of the clicked slot (-999 if outside of inventory, else -1 if not on slot)
     * @return The {@link ItemStack} to be left in the clicked slot
     */
    @Override
    public ItemStack transferSlot(PlayerEntity player, int invSlot) {
        this.sendContentUpdates();
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            if (invSlot < this.inventory.size()) {
                if (!this.insertItem(originalStack, this.inventory.size(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(originalStack, 0, this.inventory.size(), false)) {
                return ItemStack.EMPTY;
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        this.sendContentUpdates();
        return newStack;
    }

    /**
     * Starts the crafting animation and stores the molecule that's being crafted
     *
     * @param molecule The {@link Molecule} that will be crafted once the animation is done
     */
    public void craftingAnimation(Molecule molecule) {
        this.currentMolecule = molecule;
    }

    /**
     * Gets called whenever the inventory gets changed
     *
     * @param inventory The changed inventory
     */
    @Override
    public void onContentChanged(Inventory inventory) {
        super.onContentChanged(inventory);

        boolean inputEmpty = true;
        for (int i = 25; i < 34; i++) {
            if (this.getSlot(i).getStack().getCount() != 0) {
                inputEmpty = false;
                break;
            }
        }

        this.locked = !inputEmpty;

        Atom[][] atoms = new Atom[5][5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                Item item = this.inventory.getStack(i * 5 + j).getItem();
                atoms[i][j] = item instanceof AtomItem ? ((AtomItem) item).getAtom() : null;
            }
        }

        Map<Atom, Integer> ingredients = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                if (atoms[i][j] != null)
                    ingredients.put(atoms[i][j], ingredients.getOrDefault(atoms[i][j], 0) + 1);
            }
        }

        context.run((world, pos) -> updateContent(world));
    }

    /**
     * Creates a {@link LewisCraftingGrid} from the 5x5 grid
     *
     * @return the created {@code LewisCraftingGrid}
     */
    public LewisCraftingGrid getLewisCraftingGrid() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            stacks.add(inventory.getStack(i));
        }
//        Scicraft.LOGGER.info("stacks: " + stacks);

        return new LewisCraftingGrid(stacks.toArray(new ItemStack[0]));
    }

    private void updateContent(World world) {
//        LewisCraftingGrid grid = getLewisCraftingGrid();
//        Optional<MoleculeRecipe> recipe = world.getRecipeManager().getFirstMatch(MoleculeRecipe.MOLECULE_CRAFTING, grid, world);
//
//
//        if (recipe.isEmpty()) {
//            if (isInputOpen()) closeInputSlots();
//            return;
//        }
//
//        MoleculeRecipe moleculeRecipe = recipe.get();
//
//        if (!isInputOpen() || !inventory.isEmpty()) {
//            //Scicraft.LOGGER.info("Opening input");
//            for (int m = 0; m < 9; ++m) {
//                ((LewisInputSlot)getSlot(m + 25)).setDensity(moleculeRecipe.getDensity());
//            }
//
//            ArrayList<Atom> atoms = new ArrayList<>(moleculeRecipe.getMolecule().getIngredients());
//            atoms.sort(Comparator.comparingInt(a -> DelegateSettings.ATOM_MAPPINGS.get(a.getItem())));
//            openInputSlots(atoms);
//        }
//
//        if (hasCorrectInput(moleculeRecipe.getMolecule(),moleculeRecipe)) {
//            ItemStack itemStack = new ItemStack(moleculeRecipe.getMolecule().getItem(), outputSlot.getStack().getCount());
//            if (this.propertyDelegate.get(1) == -1 && (outputSlot.getStack().isEmpty() || ItemStack.areEqual(outputSlot.getStack(), itemStack)))
//                this.craftingAnimation(moleculeRecipe.getMolecule());
//        } else {
//            //arrow is running but input is no longer valid
//            if (this.propertyDelegate.get(0) >= 0 && this.propertyDelegate.get(0) < 23) {
//                //stop crafting animation
//                propertyDelegate.set(0, -1);
//                this.currentMolecule = null;
//            }
//        }
    }

    private boolean hasCorrectInput(Molecule molecule,MoleculeRecipe moleculeRecipe) {
        List<Atom> ingredients = new ArrayList<>(molecule.getIngredients());
        ingredients.sort(Comparator.comparingInt(o -> DelegateSettings.ATOM_MAPPINGS.get(o.getItem())));
        boolean isCorrect = true;
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack stack = inventory.getStack(i + 25);
            if (stack == null || stack.isEmpty() || !ingredients.get(i).getItem().equals(stack.getItem()) || stack.getCount() != moleculeRecipe.getDensity()) {
                isCorrect = false;
            } else {

            }
        }
        return isCorrect && erlenmeyerSlot.getStack().getItem().equals(Items.ERLENMEYER);
    }

    /**
     * Sets the crafted item in the output slot,
     * clears the input slots and clears the saved molecule
     *
     * @return Returns false if no molecule was saved (and the operation failed)
     */
    public boolean setOutput() {
        if (currentMolecule == null) return false;
        if (outputSlot.hasStack()) outputSlot.getStack().increment(1);
        else outputSlot.setStack(currentMolecule.getItem().getDefaultStack());
        clearInput(true);
        currentMolecule = null;
        return true;
    }

    /**
     * @return Returns true if the input slots are enabled
     */
    protected boolean isInputOpen() {
        return this.getSlot(25).isEnabled();
    }

    /**
     * @return Returns true if the input slots are empty
     */
    protected boolean isInputEmpty() {
        for (int i = 0; i < 9; i++) {
            if (!this.getSlot(i + 25).getStack().isEmpty()) return false;
        }
        return true;
    }

    /**
     * Clears the input slots
     *
     * @param erlenmeyer Whether to take an erlenmeyer out as well
     *                   (if the recipe uses it)
     */
    protected void clearInput(boolean erlenmeyer) {
        if (erlenmeyer) erlenmeyerSlot.getStack().decrement(1);
        for (int i = 0; i < 9; i++) {
            this.getSlot(i + 25).setStack(ItemStack.EMPTY);
        }
    }

    /**
     * Opens the required input slots for the given {@link Atom}{@code s}
     *
     * @param atoms {@link List} of {@code Atoms} to allow in the slots
     */
    protected void openInputSlots(@NotNull List<Atom> atoms) {
        int slotItems = 1;
        for (int i = 0; i < atoms.size(); i++) {
            Item atom = atoms.get(i).getItem();
            ((LewisInputSlot) this.getSlot(i + 25)).setAllowedItem(atom);
            this.sendContentUpdates();
        }
    }

    /**
     * Closes the input slots
     */
    protected void closeInputSlots() {
        for (int i = 25; i < 34; i++) {
            ((LewisInputSlot) this.getSlot(i)).setAllowedItem(null);
            this.sendContentUpdates();
        }
        //closeErlenmeyer();
    }

    public int getProgress() {
        return this.propertyDelegate.get(0);
    }
}
