package net.torocraft.toroquest.entities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.IInventoryChangedListener;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.util.datafix.walkers.ItemStackDataLists;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.server.command.TextComponentHelper;
import net.torocraft.toroquest.ToroQuest;
import net.torocraft.toroquest.civilization.CivilizationDataAccessor;
import net.torocraft.toroquest.civilization.CivilizationHandlers;
import net.torocraft.toroquest.civilization.CivilizationType;
import net.torocraft.toroquest.civilization.CivilizationUtil;
import net.torocraft.toroquest.civilization.CivilizationsWorldSaveData;
import net.torocraft.toroquest.civilization.Province;
import net.torocraft.toroquest.civilization.player.PlayerCivilizationCapabilityImpl;
import net.torocraft.toroquest.config.ToroQuestConfiguration;
import net.torocraft.toroquest.entities.ai.AIHelper;
import net.torocraft.toroquest.entities.ai.EntityAIRaid;
import net.torocraft.toroquest.entities.render.RenderVillageLord;
import net.torocraft.toroquest.gui.VillageLordGuiHandler;
import net.torocraft.toroquest.inventory.IVillageLordInventory;
import net.torocraft.toroquest.inventory.VillageLordInventory;
import net.torocraft.toroquest.item.armor.ItemRoyalArmor;

public class EntityVillageLord extends EntityToroNpc implements IInventoryChangedListener
{

	protected int isAnnoyedTimer = 0;

	protected EntityLivingBase underAttack = null;
	protected int underAttackTimer = 0;
	
	protected EntityPlayer murderWitness = null;
	protected int murderTimer = 0;
	
	protected boolean inCombat = false;
	
	public static String NAME = "village_lord";
	
	static
	{
		if (ToroQuestConfiguration.specificEntityNames)
		{
			NAME = ToroQuestEntities.ENTITY_PREFIX + NAME;
		}
	}
	
	@Override
	public boolean getAlwaysRenderNameTag()
    {
        return false;
    }
	
	public static void init(int entityId)
	{
		EntityRegistry.registerModEntity(new ResourceLocation(ToroQuest.MODID, NAME), EntityVillageLord.class, NAME, entityId, ToroQuest.INSTANCE, 80,
				3, true, 0xeca58c, 0xba12c8);
	}

	public static void registerRenders()
	{
		RenderingRegistry.registerEntityRenderingHandler(EntityVillageLord.class, new IRenderFactory<EntityVillageLord>() {
			@Override
			public Render<EntityVillageLord> createRenderFor(RenderManager manager) {
				return new RenderVillageLord(manager);
			}
		});
	}
	
	@Override
	public void setAttackTarget(EntityLivingBase e)
	{
		super.setAttackTarget(null);
	}
	
	// =-=-=-=-=-=-=-=-=-=-=-=-=-= TROHPY =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
	protected int getInventorySize()
	{
		// return VillageLordContainer.LORD_INVENTORY_SLOT_COUNT;
		return 14;
	}
	// =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

	public void onLivingUpdate()
	{
		super.onLivingUpdate();
		
		if ( this.world.isRemote )
		{
			return;
		}
		
		if ( this.getAttackTarget() != null )
    	{
    		AIHelper.faceEntitySmart(this, this.getAttackTarget());
    		this.getLookHelper().setLookPositionWithEntity(this.getAttackTarget(), 30.0F, 30.0F);
    	}
		
		if ( this.ticksExisted % 100 == 0 )
		{
			if ( this.getHealth() >= this.getMaxHealth() )
			{
				this.hitSafety = true;
			}
			else this.heal(1.0f);
			
			this.isAnnoyedTimer--;
			
			if ( !this.actionReady() )
    		{
    			this.actionTimer--;
    		}
    		
    		if ( this.underAttackTimer > 0 )
    		{
    			if ( --this.underAttackTimer < 1 )
    			{
    				this.underAttack = null;
    			}
    		}
    		
    		if ( this.murderTimer > 0 )
    		{
    			if ( --this.murderTimer < 1 )
    			{
    				this.murderWitness = null;
    			}
    		}
    		
			BlockPos pos = EntityAIRaid.findValidSurface(this.world, new BlockPos(this.posX, this.posY, this.posZ), 8);
			
			if ( pos != null && !this.getNavigator().tryMoveToXYZ(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5, 0.25D))
			{
				this.returnToPost();
			}
    		
			if ( this.ticksExisted % 500 == 0 && this.isEntityAlive() )
			{
				this.talkingWith = null;
				this.pledgeAllegianceIfUnaffiliated(true);
			}
		}
	}
	
	// ============================= PLEDGE ==============================
	// ======================== PLEDGE ALLEGIANCE =========================
	/* returns TRUE if allegiance is set by this method */
	protected boolean pledgeAllegianceIfUnaffiliated( boolean force )
	{
		if ( force || this.getProvince() == null || this.getUUID() == null || this.getCivilization() == null )
		{
			Province prov = CivilizationUtil.getProvinceAt(this.world, this.chunkCoordX, this.chunkCoordZ);
			
			this.setHasLord(true, prov);

			if ( this.pledgeAllegiance(prov) )
			{
				this.playTameEffect((byte)6);
		        this.world.setEntityState(this, (byte)6);
		        return true;
			}
		}
		return false;
	}
	
	@Override
	public void onPledge( Province prov )
	{
		
	}
	
	public boolean returnToPost()
	{
		if ( this.raidX == null || this.raidZ == null || ( this.raidX == 0 && this.raidZ == 0 ) )
		{
			return false;
		}
		
		if ( this.hasPath() )
		{
			return false;
		}
		
		double x = this.raidX - this.posX;
		double z = this.raidZ - this.posZ;
		
		double xz = Math.abs(x) + Math.abs(z);
		
		if ( xz < 4 )
		{
			return false;
		}
		
		x = x/xz * 12 + this.posX + (this.rand.nextInt(3)-1);
		z = z/xz * 12 + this.posZ + (this.rand.nextInt(3)-1);
		
		BlockPos moveTo = EntityAIRaid.findValidSurface(this.world, new BlockPos(x, this.posY, z), 8);
		
		if ( moveTo != null )
		{
			if ( this.getNavigator().tryMoveToXYZ(moveTo.getX(), moveTo.getY(), moveTo.getZ(), 0.4D) )
			{
				return true;
			}
		}
				
		Vec3d vec3d = RandomPositionGenerator.findRandomTargetBlockTowards(this, 2, 8, new Vec3d(x,this.posY,z));
		
		if ( vec3d == null || !this.getNavigator().tryMoveToXYZ(vec3d.x, vec3d.y, vec3d.z, 0.4D) )
        {
			vec3d = RandomPositionGenerator.findRandomTargetBlockTowards(this, 4, 8, new Vec3d(x,this.posY,z));
			
			if ( vec3d == null || !this.getNavigator().tryMoveToXYZ(vec3d.x, vec3d.y, vec3d.z, 0.4D ) )
			{
				vec3d = RandomPositionGenerator.findRandomTargetBlockTowards(this, 8, 8, new Vec3d(x,this.posY,z));
				
				if ( vec3d == null || !this.getNavigator().tryMoveToXYZ(vec3d.x, vec3d.y, vec3d.z, 0.4D ) )
				{
					return false;
				}
			}
        }
		
		return true;
	}

//	private BlockPos getSurfacePosition(World world, int x, int z)
//	{
//		int i = 8;
//		BlockPos search = new BlockPos(x, this.posY+i, z);
//		IBlockState blockState;
//		while ( i > 0 )
//		{
//			i++;
//			search = search.down();
//			blockState = world.getBlockState(search);
//			if (isLiquid(blockState))
//			{
//				break;
//			}
//			if ((blockState).isOpaqueCube())
//			{
//				break;
//			}
//		}
//		return search.up();
//	}
	
//	private boolean isLiquid(IBlockState blockState)
//	{
//		return blockState.getBlock() == Blocks.WATER || blockState.getBlock() == Blocks.LAVA;
//	}

	protected Map<UUID, VillageLordInventory> inventories = new HashMap<UUID, VillageLordInventory>();

	public EntityVillageLord(World world)
	{
		super(world, null);
		this.initInventories();
	}

	public IVillageLordInventory getInventory(UUID playerId)
	{
		if (inventories.get(playerId) == null)
		{
			inventories.put(playerId, new VillageLordInventory(this, "VillageLordInventory", this.getInventorySize()));
		}
		return inventories.get(playerId);
	}

	protected void initInventories()
	{
		Map<UUID, VillageLordInventory> newInventories = new HashMap<UUID, VillageLordInventory>();
		for (UUID playerId : inventories.keySet())
		{
			newInventories.put(playerId, initInventory(inventories.get(playerId)));
		}
	}

	protected VillageLordInventory initInventory(VillageLordInventory prevInventory)
	{
		VillageLordInventory newInventory = new VillageLordInventory(this, "VillageLordInventory", this.getInventorySize());
		newInventory.setCustomName(this.getName());

		if (prevInventory != null)
		{
			prevInventory.removeInventoryChangeListener(this);
			int i = Math.min(prevInventory.getSizeInventory(), newInventory.getSizeInventory());

			for (int j = 0; j < i; ++j)
			{
				ItemStack itemstack = prevInventory.getStackInSlot(j);

				if (!itemstack.isEmpty())
				{
					newInventory.setInventorySlotContents(j, itemstack.copy());
				}
			}
		}

		newInventory.addInventoryChangeListener(this);
		return newInventory;
	}

	public void openGUI(EntityPlayer player)
	{
//		if (world.isRemote)
//		{
//			return;
//		}
		player.openGui(ToroQuest.INSTANCE, VillageLordGuiHandler.getGuiID(), this.world, getPosition().getX(), getPosition().getY(), getPosition().getZ());
	}
	
	@Override
	protected boolean processInteract(EntityPlayer player, EnumHand hand) 
	{
		if ( player == null || !this.isEntityAlive() || hand == null )
		{
			return false;
		}
		
		this.getLookHelper().setLookPositionWithEntity(player, 30.0F, 30.0F);
		this.faceEntity(player, 30.0F, 30.0F);
		this.talkingWith = player;
		
		if ( player.world.isRemote )
		{
			return false;
		}
		
		if ( this.isChild() || this.recentlyHit > 0 )
		{
			return true;
		}
		
		if ( this.getCivilization() == null || this.getProvince() == null || this.getUUID() == null || player.isInvisible() )
		{
			return true;
		}
		
		Province lordProvince = CivilizationUtil.getProvinceAt(this.world, this.chunkCoordX, this.chunkCoordZ);
		
		if ( lordProvince == null )
		{
			return true;
		}
		
		Province playerProvince = CivilizationUtil.getProvinceAt(player.world, player.chunkCoordX, player.chunkCoordZ);

		if ( playerProvince == null )
		{
			return true;
		}
		
		if ( lordProvince != playerProvince )
		{
			return true;
		}

		int rep = PlayerCivilizationCapabilityImpl.get(player).getReputation(playerProvince.civilization);
		
		if ( rep < -50 || ( this.murderWitness != null && this.murderWitness == player ) || ( this.underAttack != null && this.underAttack == player ) )
		{
			this.setAnnoyed( player );
			
			if ( this.actionReady() )
			{
				this.chat(this, player, "crime", null);
			}
			return true;
		}
		if ( this.isAnnoyedAt(player) )
		{
			this.setAnnoyed( player );

			if ( this.actionReady() )
			{
				this.chatfriendly("annoyed", player, null);
			}
			return true;
		}
		else
		{
			ItemStack itemstack = player.getHeldItem(hand);
			String name = itemstack.getDisplayName();
	        if ( itemstack.getItem() == Item.getByNameOrId("toroquest:city_key") )
	        {
	        	if ( name.equals("Key to the City") )
	        	{
					this.chatfriendly("incorrectname", player, name);
	        		return true;
	        	}
	        	else if ( rep >= 500 )
	        	{
					CivilizationDataAccessor worldData = CivilizationsWorldSaveData.get(player.world);
					if ( worldData == null )
					{
						return true;
					}
			        playerProvince.setName(name);
					worldData.setName(playerProvince.getUUID(), name);
					this.chatfriendly("rename", player, name);
					return true;
	        	}
	        	else
	        	{
	        		this.chatfriendly("renameconsider", player, name);
	        		return true;
	        	}
	        }
	        else
	        {
				this.openGUI(player);
				return true;
			}
		}
	}
	
	@Override
	public boolean canBeHitWithPotion()
    {
        return false;
    }
	
	@Override
	public String getName()
    {
        if (this.hasCustomName())
        {
            return this.getCustomNameTag();
        }
        else
        {
            return super.getName();
        }
    }
	
	private void chatfriendly( String message, EntityPlayer player, @Nullable String extra )
	{
		if ( ToroQuestConfiguration.guardsHaveDialogue )
		{
			this.getLookHelper().setLookPositionWithEntity(player, 30.0F, 30.0F);
			this.faceEntity(player, 30.0F, 30.0F);

			if ( player.world.isRemote )
			{
				return;
			}
			
			try
			{
				int i = player.world.rand.nextInt(Integer.parseInt( TextComponentHelper.createComponentTranslation(player, "entity.toroquest.lord."+message, new Object[0]).getUnformattedText() ));
				String s = TextComponentHelper.createComponentTranslation(player, "entity.toroquest.lord."+message+i, new Object[0]).getUnformattedText().replace("@p", player.getDisplayNameString());
				//String s = "§l" + this.getName() + "§r: " + (I18n.format("entity.toroquest.lord."+message+rand.nextInt(Integer.parseInt(I18n.format("entity.toroquest.lord."+message)))).replace("@p", player.getName()));
				
				if ( extra != null )
				{
					s = s.replace("@e", extra);
				}
				
				player.sendMessage(new TextComponentString("§l" + this.getName() + "§r: " + s));
			}
			catch ( Exception e )
			{
				//int i = player.world.rand.nextInt(Integer.parseInt( TextComponentHelper.createComponentTranslation(player, "entity.toroquest.lord", new Object[0]).getUnformattedText() ));
				String s = TextComponentHelper.createComponentTranslation(player, "entity.toroquest.lord."+message, new Object[0]).getUnformattedText().replace("@p", player.getDisplayNameString());
				//String s = "§l" + this.getName() + "§r: " + (I18n.format("entity.toroquest.lord."+message).replace("@p", player.getName()));
				
				if ( extra != null )
				{
					s = s.replace("@e", extra);
				}
				
				player.sendMessage(new TextComponentString("§l" + this.getName() + "§r: " + s));
			}
			
	    	player.world.playSound((EntityPlayer)null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_EVOCATION_ILLAGER_AMBIENT, SoundCategory.AMBIENT, 1.0F, 0.9F + rand.nextFloat()/5.0F );
			this.actionTimer = 2;
		}
	}
	
	// crime!
	public void chat( EntityToroNpc guard, EntityPlayer player, String message, @Nullable String extra )
	{
		guard.callForHelp(player);
		
		if ( ToroQuestConfiguration.guardsHaveDialogue && this.actionReady() )
		{
			guard.getLookHelper().setLookPositionWithEntity(player, 30.0F, 30.0F);
			guard.faceEntity(player, 30.0F, 30.0F);
			
			if ( player.world.isRemote )
			{
				return;
			}
			
			try
			{
				int i = player.world.rand.nextInt(Integer.parseInt( TextComponentHelper.createComponentTranslation(player, "entity.toroquest.guard."+message, new Object[0]).getUnformattedText() ));
				String s = TextComponentHelper.createComponentTranslation(player, "entity.toroquest.guard."+message+i, new Object[0]).getUnformattedText().replace("@p", player.getDisplayNameString());
				//String s = "§l" + guard.getName() + "§r: " + (I18n.format("entity.toroquest.guard."+message+guard.world.rand.nextInt(Integer.parseInt(I18n.format("entity.toroquest.guard."+message)))).replace("@p", player.getDisplayNameString()));
				
				if ( extra != null )
				{
					s = s.replace("@e", extra);
				}
				
				player.sendMessage(new TextComponentString("§l" + guard.getName() + "§r: " + s));
			}
			catch ( Exception e )
			{
				String s = TextComponentHelper.createComponentTranslation(player, "entity.toroquest.guard."+message, new Object[0]).getUnformattedText().replace("@p", player.getDisplayNameString());
				//String s = "§l" + guard.getName() + "§r: " + (I18n.format("entity.toroquest.guard."+message).replace("@p", player.getDisplayNameString()));
				
				if ( extra != null )
				{
					s = s.replace("@e", extra);
				}
				
				player.sendMessage(new TextComponentString("§l" + guard.getName() + "§r: " + s));
			}
			
			guard.playSound( SoundEvents.ENTITY_EVOCATION_ILLAGER_AMBIENT, 1.0F, 0.9F + guard.world.rand.nextFloat()/5.0F );
			guard.actionTimer = 2;
		}
	}
	
	// private EntityAIMoveIntoArea areaAI;
	
	@Override
	protected void initEntityAI()
	{
		super.initEntityAI();
		this.setHasLord(true, CivilizationUtil.getProvinceAt(this.world, this.chunkCoordX, this.chunkCoordZ) );
		this.tasks.addTask(0, new EntityAISwimming(this));
		this.tasks.addTask(1, new EntityAIPanic(this, 0.485D));
		this.tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F)
		{
			@Override
			public boolean shouldExecute()
		    {
		        if ( EntityVillageLord.this.talkingWith != null )
		        {
		        	this.closestEntity = EntityVillageLord.this.talkingWith;
		        	return true;
		        }
		        else
		        {
		            return false;
		        }
		    }
			
			@Override
			public boolean shouldContinueExecuting()
	        {
	        	if ( EntityVillageLord.this.talkingWith == null )
	        	{
	        		return false;
	        	}
	        	else
	        	{
	        		return super.shouldContinueExecuting();
	        	}
	        }
		});
		this.tasks.addTask(8, new EntityAILookIdle(this));
	}
		
	@Nullable
	@Override
	public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData livingdata)
	{
		livingdata = super.onInitialSpawn(difficulty, livingdata);
		this.detachHome();
		this.addArmor();
		this.setLeftHanded(false);
		return livingdata;
	}
	
	@Override
	public boolean hasHome()
	{
		return false;
	}

	public void addArmor()
	{
		setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(ItemRoyalArmor.helmetItem, 1));
		setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(ItemRoyalArmor.chestplateItem, 1));
		setItemStackToSlot(EntityEquipmentSlot.LEGS, new ItemStack(ItemRoyalArmor.leggingsItem, 1));
		setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(ItemRoyalArmor.bootsItem, 1));
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float amount)
	{
		if ( this.world.isRemote )
		{
			return false;
		}
		if ( !(source.getTrueSource() instanceof EntityLivingBase) || source.getTrueSource() instanceof EntityToroNpc ) 
		{
			return false;
		}
		
		Entity entity = source.getTrueSource();
		if ( entity instanceof EntityPlayer )
		{
            if ( this.hitSafety )
			{
				this.hitSafety = false;
				this.playSound(SoundEvents.BLOCK_CLOTH_BREAK, 1.0F, 1.0F);
				return false;
			}
			EntityPlayer player = (EntityPlayer)(entity);
			dropRepTo(player,-(int)MathHelper.clamp(amount*20,25,this.getHealth()*20));
			this.setUnderAttack(player);
			
			boolean flag = false;
			
			List<EntityGuard> guards = this.world.getEntitiesWithinAABB(EntityGuard.class, new AxisAlignedBB(this.getPosition()).grow(16, 12, 16), new Predicate<EntityGuard>()
			{
				public boolean apply(@Nullable EntityGuard entity)
				{
					return true;
				}
			});
			
			for (EntityGuard guard : guards)
			{
				if ( guard.getAttackTarget() == null )
				{
					this.getNavigator().tryMoveToEntityLiving(guard, 0.4D);
					
					if ( entity instanceof EntityPlayer ) 
					{
						guard.setAnnoyed( (EntityPlayer)entity );
						if ( !flag && guard.actionReady() && guard.getDistance(entity) <= 8.0D )
						{
							guard.chat((EntityPlayer)entity, "attacklord", null);
							flag = true;
						}
					}
					guard.setAttackTarget((EntityLivingBase) entity);
				}
			}
		}
		
		if ( entity instanceof EntityLivingBase )
		{
			this.callForHelp( (EntityLivingBase) source.getTrueSource());
		}
		
		return super.attackEntityFrom(source, amount);
	}
	
	public void callForHelp( EntityLivingBase attacker )
	{
		if ( attacker == null || !attacker.isEntityAlive() || attacker instanceof EntityToroNpc || attacker instanceof EntityVillager )
		{
			return;
		}
		
		List<EntityGuard> guards = this.getEntityWorld().getEntitiesWithinAABB(EntityGuard.class, new AxisAlignedBB(this.getPosition()).grow(25, 15, 25), new Predicate<EntityGuard>()
		{
			public boolean apply(@Nullable EntityGuard entity)
			{
				return true;
			}
		});
		
		for (EntityGuard guard: guards)
		{
			//if ( guard.getAttackTarget() == null && guard.canEntityBeSeen( attacker ) )
			{
				if ( attacker instanceof EntityPlayer )
				{
					guard.setAnnoyed( (EntityPlayer)attacker );
				}
				guard.setAttackTarget( attacker );
			}
		}
		
//		if ( this.getAttackTarget() == null || !this.getAttackTarget().isEntityAlive() )
//		{
//			if ( this.getRevengeTarget() != null )
//			{
//				if ( this.getRevengeTarget() instanceof EntityPlayer )
//				{
//					this.setAnnoyed( (EntityPlayer)attacker );
//				}
//				this.setAttackTarget( this.getRevengeTarget() );
//			}
//		}
		
//		if ( this.getAttackTarget() != null && !this.getAttackTarget().isEntityAlive() )
//		{
//			this.setAttackTarget(null);
//		}
		
		List<EntityToroVillager> villagers = attacker.getEntityWorld().getEntitiesWithinAABB(EntityToroVillager.class, new AxisAlignedBB(getPosition()).grow(20, 12, 20), new Predicate<EntityToroVillager>()
		{
			public boolean apply(@Nullable EntityToroVillager entity)
			{
				return true;
			}
		});
	
		for ( EntityToroVillager villager : villagers )
		{
			villager.setUnderAttack(attacker);
		}
		
//		if ( attacker == null )
//		{
//			return;
//		}
//		
//		if ( attacker instanceof EntityPlayer ) 
//		{
//			this.setUnderAttack((EntityPlayer)attacker);
//		}
//		
//		List<EntityGuard> guards = this.world.getEntitiesWithinAABB(EntityGuard.class, new AxisAlignedBB(this.getPosition()).grow(25, 15, 25), new Predicate<EntityGuard>()
//		{
//			public boolean apply(@Nullable EntityGuard entity)
//			{
//				return true;
//			}
//		});
//		
//		for (EntityGuard guard: guards)
//		{
//			// if ( guard.getAttackTarget() == null && guard.canEntityBeSeen( attacker ) )
//			{
//				if ( attacker instanceof EntityPlayer ) guard.setAnnoyed((EntityPlayer)attacker);
//				guard.setAttackTarget( attacker );
//			}
//		}
//		
//		if ( this.getAttackTarget() == null || !this.getAttackTarget().isEntityAlive() )
//		{
//			if ( this.getRevengeTarget() instanceof EntityPlayer ) this.setAnnoyed((EntityPlayer)attacker);
//			this.setAttackTarget( this.getRevengeTarget() );
//		}
//		
//		List<EntityToroVillager> villagers = world.getEntitiesWithinAABB(EntityToroVillager.class, new AxisAlignedBB(getPosition()).grow(12, 8, 12), new Predicate<EntityToroVillager>()
//		{
//			public boolean apply(@Nullable EntityToroVillager entity)
//			{
//				return true;
//			}
//		});
//
//		for ( EntityToroVillager villager : villagers )
//		{
//			//if ( villager.canEntityBeSeen(attacker) )
//			{
//				villager.setUnderAttack(attacker);
//			}
//		}
	}
	
    // Village village = null;

//    @Override
//    public Village getVillage()
//    {
//        return this.village = this.world.getVillageCollection().getNearestVillage(new BlockPos(this), 88);
//    }

	private boolean dropRepTo(Entity entity, int amount)
	{
		if (entity == null)
		{
			return false;
		}

		if (!(entity instanceof EntityPlayer))
		{
			return false;
		}

		EntityPlayer player = (EntityPlayer) entity;

		CivilizationType civ = getCivilization();
		if (civ == null)
		{
			return false;
		}
		CivilizationHandlers.adjustPlayerRep(player, civ, amount);
		return true;
	}

	@Override
	public void onInventoryChanged(IInventory invBasic)
	{

	}

	public void onDeath(DamageSource cause)
	{
		Province province = CivilizationUtil.getProvinceAt(this.world, this.chunkCoordX, this.chunkCoordZ);

		this.setHasLord(false, province);
		
		if ( world.isRemote ) // inventories == null
		{
			return;
		}
		
		if ( inventories != null )
		{
			this.killer(cause);
		}
		
		this.replaceItemInInventory(100 + EntityEquipmentSlot.HEAD.getIndex(), ItemStack.EMPTY);
		this.dropLoot();
		super.onDeath(cause);
	}
	
	public void dropLoot()
	{
		if ( !world.isRemote && this.ticksExisted > 500 )
		{
			ItemStack stack = new ItemStack(Items.EMERALD, 3);
			EntityItem dropItem = new EntityItem(world, posX, posY, posZ, stack.copy());
			world.spawnEntity(dropItem);
			
			if ( this.getCivilization() != null )
			{
				ItemStack head = new ItemStack(Item.getByNameOrId("toroquest:royal_helmet"), 1);
				EntityItem dropHead = new EntityItem(world, posX, posY, posZ, head.copy());
				world.spawnEntity(dropHead);
			}
		}
	}

	protected void killer(DamageSource cause)
	{
		if ( world.isRemote )
		{
			return;
		}
		
		Entity entity = cause.getTrueSource();
		
		if (entity != null && entity instanceof EntityPlayer)
		{
			if ( dropRepTo( entity, -3000 ) )
			{
				for (IVillageLordInventory inventory : inventories.values())
				{
					dropInventory(inventory);
				}
			}
		}
		
		for ( EntityPlayer player : this.world.playerEntities )
		{
			if ( this.getProvince() == null || getProvince().equals("") )
			{
				player.sendStatusMessage( TextComponentHelper.createComponentTranslation(player, "entity.toroquest.lord.slaingeneric", new Object[0]), true);
			}
			else if ( getProvince() != null )
			{
				player.sendStatusMessage(   new TextComponentString(( TextComponentHelper.createComponentTranslation(player, "entity.toroquest.lord.slaincivilization", new Object[0]).getUnformattedText() ).replace("@e", getProvince())), true   );
			}
		}
	}

	protected void dropInventory(IVillageLordInventory inventory)
	{
		if (inventory == null)
		{
			return;
		}
		for (int i = 0; i < inventory.getSizeInventory(); ++i)
		{
			ItemStack itemstack = inventory.getStackInSlot(i);
			if (!itemstack.isEmpty())
			{
				entityDropItem(itemstack, 0.0F);
			}
		}
	}

	public static void registerFixesVillageLord(DataFixer fixer)
	{
		EntityLiving.registerFixesMob(fixer, EntityVillageLord.class);
		fixer.registerWalker(FixTypes.ENTITY, new ItemStackDataLists(EntityVillageLord.class, new String[] { "Items" }));
	}

	private void setHasLord(boolean hasLord, Province province)
	{		
		if ( province == null )
		{
			return;
		}
		
		CivilizationDataAccessor worldData = CivilizationsWorldSaveData.get(world);
		
		if ( worldData.provinceHasLord(province.id) == hasLord )
		{
			return;
		}

		if ( !isEntityAlive() && hasLord )
		{
			hasLord = false;
		}
		
		if ( !hasLord )
		{
			worldData.setTrophyPig(province.id, false);
			worldData.setTrophyMage(province.id, false);
			worldData.setTrophySpider(province.id, false);
			worldData.setTrophySkeleton(province.id, false);
			worldData.setTrophyBandit(province.id, false);
			worldData.setTrophyLord(province.id, false);
			worldData.setTrophyTitan(province.id, false);
			worldData.setTrophyBeholder(province.id, false);
		}
		
		worldData.setProvinceHasLord(province.id, hasLord);
	}

@Override
public void writeEntityToNBT(NBTTagCompound compound)
{
	NBTTagCompound c = new NBTTagCompound();
	for (Entry<UUID, VillageLordInventory> e : inventories.entrySet())
	{
		c.setTag(e.getKey().toString(), e.getValue().saveAllItems());
	}
	compound.setTag("Items", c);
	super.writeEntityToNBT(compound);
}

	@Override
	public void readEntityFromNBT(NBTTagCompound compound)
	{
		NBTTagCompound c = compound.getCompoundTag("Items");
		inventories = new HashMap<UUID, VillageLordInventory>();
		for (String sPlayerId : c.getKeySet())
		{
			VillageLordInventory inv = new VillageLordInventory(this, "VillageLordInventory", getInventorySize());
			inv.loadAllItems(c.getTagList(sPlayerId, 10));
			inventories.put(UUID.fromString(sPlayerId), inv);
		}
		super.readEntityFromNBT(compound);
	}

}
