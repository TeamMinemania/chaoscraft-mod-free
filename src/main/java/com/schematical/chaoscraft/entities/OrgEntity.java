package com.schematical.chaoscraft.entities;

import com.mojang.authlib.GameProfile;
import com.schematical.chaoscraft.ChaosCraft;
import com.schematical.chaoscraft.ai.CCObservableAttributeManager;
import com.schematical.chaoscraft.ai.CCObserviableAttributeCollection;
import com.schematical.chaoscraft.events.CCWorldEvent;
import com.schematical.chaoscraft.events.OrgEvent;
import com.schematical.chaoscraft.fitness.EntityFitnessManager;
import com.schematical.chaosnet.model.ChaosNetException;
import it.unimi.dsi.fastutil.ints.IntList;
import jdk.nashorn.internal.codegen.Compiler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.command.arguments.EntityAnchorArgument;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.RecipeItemHelper;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ObjectHolder;

import java.util.ArrayList;
import java.util.List;

public class OrgEntity extends LivingEntity {

    @ObjectHolder("chaoscraft:org_entity")
    public static final EntityType<OrgEntity> ORGANISM_TYPE = null;
    public final double REACH_DISTANCE = 5.0D;

    public final NonNullList<ItemStack> orgInventory = NonNullList.withSize(36, ItemStack.EMPTY);
    public EntityFitnessManager entityFitnessManager;

    protected CCPlayerEntityWrapper playerWrapper;
    public CCObservableAttributeManager observableAttributeManager;
    public List<OrgEvent> events = new ArrayList<OrgEvent>();

    //The current selected item
    private int currentItem = 0;
    protected ItemStackHandler itemHandler = new ItemStackHandler();
    protected double desiredPitch;
    protected double desiredYaw;
    //Whether the bot has tried left clicking last tick.
    private boolean lastTickLeftClicked;

    //Mining related variables
    private float hardness = 0;
    private BlockPos lastMinePos = BlockPos.ZERO;
    private int blockSoundTimer;
    private float maxLifeSeconds;
    private int miningTicks = 0;

    public List<AlteredBlockInfo> alteredBlocks = new ArrayList<AlteredBlockInfo>();
    private int equippedSlot = -1;

    public OrgEntity(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
    }

    @Override
    public Iterable<ItemStack> getArmorInventoryList() {
        return new ArrayList<ItemStack>();
    }

    @Override
    public ItemStack getItemStackFromSlot(EquipmentSlotType slotIn) {


        return ItemStack.EMPTY;
    }

    @Override
    public void setItemStackToSlot(EquipmentSlotType slotIn, ItemStack stack) {

    }
    public ItemStackHandler getItemStack(){
        return this.itemHandler;
    }

    @Override
    public HandSide getPrimaryHand() {
        return null;
    }


    public String getCCNamespace() {
        return "...TODO: Stuff";
    }

    public boolean isEntityInLineOfSight(LivingEntity target, double blockReachDistance) {
        Vec3d vec3d = this.getEyePosition(1);
        Vec3d vec3d1 = this.getLook(1);
        Vec3d vec3d2 = vec3d.add(
                new Vec3d(
                        vec3d1.x * blockReachDistance,
                        vec3d1.y * blockReachDistance,
                        vec3d1.z * blockReachDistance
                )
        );

        return target.getCollisionBoundingBox().rayTrace(vec3d, vec3d2).isPresent();
    }
    public void setDesiredPitch(double _desiredPitch){
        this.desiredPitch = _desiredPitch;
    }
    public void updatePitchYaw(){
        double yOffset = Math.sin(Math.toRadians(desiredPitch));
        double zOffset = Math.cos(Math.toRadians(this.desiredYaw)) * Math.cos(Math.toRadians(desiredPitch));
        double xOffset = Math.sin(Math.toRadians(this.desiredYaw)) * Math.cos(Math.toRadians(desiredPitch));
        this.lookAt(EntityAnchorArgument.Type.EYES, new Vec3d(getPositionVec().x + xOffset, getPositionVec().y + this.getEyeHeight() + yOffset, getPositionVec().z + zOffset));
        this.renderYawOffset = 0;
        this.setRotation(this.rotationYaw, this.rotationPitch);

    }
    @Override
    public void livingTick() {
        updatePitchYaw();
        super.livingTick();

    }
    public CCPlayerEntityWrapper getPlayerWrapper(){
        if(playerWrapper == null){
            GameProfile gameProfile = new GameProfile(null, this.getCCNamespace());
            playerWrapper = new CCPlayerEntityWrapper(world, gameProfile);
            playerWrapper.entityOrganism = this;
        }
        playerWrapper.prevRotationPitch = this.prevRotationPitch;
        playerWrapper.rotationPitch  = this.rotationPitch;
        playerWrapper.prevRotationYaw  = this.prevRotationYaw;
        playerWrapper.rotationYaw = this.rotationYaw;
        playerWrapper.prevPosX  = this.prevPosX;
        playerWrapper.prevPosY  = this.prevPosY;
        playerWrapper.prevPosZ  = this.prevPosZ;
        playerWrapper.setPosition(getPositionVec().x, getPositionVec().y, getPositionVec().z);
        playerWrapper.onGround = this.onGround;
        //playerWrapper.setHeldItem(EnumHand.MAIN_HAND, getHeldItemMainhand());
        return playerWrapper;
    }

    public boolean canCraft(IRecipe recipe) {
        //Check to see if they have the items in inventory for that


        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if(ingredients.size() == 0){
            return false;
        }
        RecipeItemHelper recipeItemHelper = getRecipeItemHelper();

        boolean result = recipeItemHelper.canCraft(recipe, null);

        return result;

    }
    public RecipeItemHelper getRecipeItemHelper(){

        RecipeItemHelper recipeItemHelper = new RecipeItemHelper();


        for(int i = 0; i < orgInventory.size(); i++) {
            ItemStack itemStack = orgInventory.get(i);

            if(!itemStack.isEmpty()){
                recipeItemHelper.accountStack(itemStack);
            }
        }
        return recipeItemHelper;


    }


    public ItemStack craft(IRecipe recipe) {
        NonNullList<Ingredient> recipeItems = null;
      /*  if(recipe instanceof ShapedRecipes) {
            recipeItems = ((ShapedRecipes) recipe).recipeItems;
        }else if(recipe instanceof ShapelessRecipes) {
            recipeItems = ((ShapelessRecipes) recipe).recipeItems;
        }else{
            throw new ChaosNetException("Found a recipe unaccounted for: " + recipe.getType().toString() + "Class Name: " +  recipe.getClass().getName());
        }*/
        recipeItems = recipe.getIngredients();
        //Check to see if they have the items in inventory for that
        RecipeItemHelper recipeItemHelper = new RecipeItemHelper();
        int slots = orgInventory.size();
        int emptySlot = -1;

        List<Integer> usedSlots = new ArrayList<Integer>();
        for(Ingredient ingredient: recipeItems) {

            for (int i = 0; i < slots; i++) {
                ItemStack itemStack = orgInventory.get(i);
                if(itemStack.isEmpty()) {
                    emptySlot = i;
                }else{
                    int packedItem = RecipeItemHelper.pack(itemStack);
                    IntList ingredientItemIds = ingredient.getValidItemStacksPacked();
                    if (ingredientItemIds.contains(packedItem)) {
                        //int amountTaken = recipeItemHelper.tryTake(packedItem, 1);
                        if (orgInventory.get(i).getCount() < 1) {
                            throw new ChaosNetException("Cannot get any more of these");
                        }
                        orgInventory.remove(i);
                    }
                }


            }

        }

        ItemStack outputStack = recipe.getRecipeOutput().copy();
        //ChaosCraft.logger.info(this.getCCNamespace() + " - Crafted: " + outputStack.getDisplayName());
        if(emptySlot != -1) {
            orgInventory.add(emptySlot, outputStack);
            observableAttributeManager.ObserveCraftableRecipes(this);
        }else{

            entityDropItem(outputStack.getItem(), outputStack.getCount());
            outputStack.setCount(0);
        }

        return outputStack;
    }
    public ItemStack getStackInSlot( EquipmentSlotType slotIn) throws Exception {
        if (slotIn == EquipmentSlotType.MAINHAND) {
            return this.orgInventory.get(currentItem);
        }
        throw new Exception("TODO: Build inventory for :" + slotIn.getName());
    }
    public float getMaxLife(){
        return maxLifeSeconds;
    }
    public void adjustMaxLife(int life) {
        maxLifeSeconds += life;
    }
    public BlockRayTraceResult rayTraceBlocks(double blockReachDistance) {
        Vec3d vec3d = this.getEyePosition(1);
        Vec3d vec3d1 = this.getLook(1);
        Vec3d vec3d2 = vec3d.add(
                new Vec3d(
                        vec3d1.x * blockReachDistance,
                        vec3d1.y * blockReachDistance,
                        vec3d1.z * blockReachDistance
                )
        );

        return this.world.rayTraceBlocks(new RayTraceContext(vec3d, vec3d2, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.ANY, this));
    }


    public void dig(BlockPos pos) {

        if (!this.world.getWorldBorder().contains(pos) || pos.distanceSq(getPosition()) > REACH_DISTANCE * REACH_DISTANCE) {
            resetMining();
            return;
        }

        if (!lastMinePos.equals(pos)) {
            resetMining();
        }

        lastMinePos = pos;

        miningTicks++;

        BlockState state = world.getBlockState(pos);

        Material material = state.getMaterial();
        if(
                material == Material.WATER ||
                        material == Material.AIR ||
                        material == Material.LAVA
        ){
            return;
        }

        this.world.sendBlockBreakProgress(this.getEntityId(), pos, (int) (state.getPlayerRelativeBlockHardness(this.getPlayerWrapper(), world, pos) * miningTicks * 10.0F) - 1);


        boolean harvest = state.getBlock().canHarvestBlock(state, world, pos, this.getPlayerWrapper());

        ItemStack stack = getHeldItemMainhand();
        //String tool = state.getBlock().getHarvestTool(state);


        //Check if block has been broken
        if (state.getPlayerRelativeBlockHardness(this.getPlayerWrapper(), world, pos) * miningTicks > 1.0f) {
            //Broken
            miningTicks = 0;

            world.playEvent(2001, pos, Block.getStateId(state));





            alteredBlocks.add(
                    new AlteredBlockInfo(
                            pos,
                            state
                    )
            );
            boolean bool = world.setBlockState(pos, Blocks.AIR.getDefaultState(), world.isRemote ? 11 : 3);
            if (bool) {
                state.getBlock().onPlayerDestroy(world, pos, state);
            } else {
                harvest = false;
            }
            //ChaosCraft.logger.info(this.getName() + " Mining: " + state.getBlock().getRegistryName().toString() +  " Held Stack: " + stack.getItem().getRegistryName().toString() + "  Harvest: "  + harvest);

            if (harvest) {
                state.getBlock().harvestBlock(world, this.getPlayerWrapper(), pos, state, world.getTileEntity(pos), stack);
                CCWorldEvent worldEvent = new CCWorldEvent(CCWorldEvent.Type.BLOCK_MINED);
                worldEvent.block = state.getBlock();
                entityFitnessManager.test(worldEvent);
            }

        }
    }

    private void resetMining() {
        miningTicks = 0;
        this.world.sendBlockBreakProgress(this.getEntityId(), lastMinePos, -1);
        this.lastMinePos.down(255);
    }
    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();

        if(!world.isRemote) {
            replaceAlteredBlocks();
           /* if (chunkTicket != null) {
                ForgeChunkManager.releaseTicket(chunkTicket);
                chunkTicket = null;
            }*/
        }
    }
    public void replaceAlteredBlocks(){
        ChaosCraft.LOGGER.info(this.getCCNamespace() + " - Trying to replace blocks - Count: " + alteredBlocks.size());
        for (AlteredBlockInfo alteredBlock : alteredBlocks) {
            boolean bool = world.setBlockState(alteredBlock.blockPos, alteredBlock.state, world.isRemote ? 11 : 3);
            String debugText = this.getCCNamespace() + " - Replacing: " + alteredBlock.state.getBlock().getRegistryName();
            if (bool) {
                ChaosCraft.LOGGER.info(debugText + " - Success");
            }else{
                ChaosCraft.LOGGER.info(debugText + " - Fail");
            }
        }
        alteredBlocks.clear();
    }
    public ItemStack equip(String resourceId) {

        ItemStackHandler itemStackHandler = getItemStack();
        int slots = itemStackHandler.getSlots();
        for(int i = 0; i < slots; i++) {
            ItemStack itemStack = itemStackHandler.getStackInSlot(i);
            if(!itemStack.isEmpty()){
                CCObserviableAttributeCollection observiableAttributeCollection = observableAttributeManager.Observe(itemStack.getItem());
                if(
                        observiableAttributeCollection != null &&
                                observiableAttributeCollection.resourceId.equals(resourceId)
                ){
                    this.setHeldItem(Hand.MAIN_HAND, itemStack);
                    equippedSlot = i;
                    return itemStack;
                }
            }
        }
        return null;
    }

    public int hasInInventory(Item item){
        //Check if it is in their inventory
        ItemStack stack = null;
        int slot = -1;
        for (int i = 0; i < this.orgInventory.size(); i++) {
            ItemStack checkStack = this.orgInventory.get(i);
            Item _item = checkStack.getItem();

            if(_item.equals(item)){
                slot = i;
            }

        }
        if (stack == null  ||stack.isEmpty()) {
            return -1;
        }
        return slot;
    }
}