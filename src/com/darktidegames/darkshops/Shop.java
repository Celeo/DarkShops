package com.darktidegames.darkshops;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_5_R3.block.CraftChest;
import org.bukkit.craftbukkit.v1_5_R3.block.CraftSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import de.bananaco.bpermissions.api.ApiLayer;
import de.bananaco.bpermissions.api.CalculableType;
import de.bananaco.bpermissions.api.Permission;

/**
 * <b>Shop</b>
 * 
 * @author Celeo
 */
public class Shop
{

	private final DarkShops plugin;
	private final int id;
	private boolean open = false;
	private String owner;
	private CraftSign sign;
	private CraftChest chest;
	private String buying;
	private String selling;
	private boolean modShop = false;

	/**
	 * Used for loading the shop from memory by plugging all the values manually
	 * 
	 * @param plugin
	 *            Signs
	 * @param id
	 *            int
	 */
	public Shop(DarkShops plugin, int id)
	{
		this.plugin = plugin;
		this.id = id;
	}

	/**
	 * Used for setting up the shop the first time
	 * 
	 * @param plugin
	 *            Signs
	 * @param id
	 *            int
	 * @param sign
	 *            CraftSign
	 * @param chest
	 *            CraftChest
	 * @param buying
	 *            String
	 * @param selling
	 *            String
	 * @param modShop
	 *            boolean
	 */
	public Shop(DarkShops plugin, int id, CraftSign sign, CraftChest chest, String buying, String selling, boolean modShop)
	{
		this(plugin, id);
		this.sign = sign;
		this.chest = chest;
		this.buying = buying;
		this.selling = selling;
		this.modShop = modShop;
		tryOpen(false);
	}

	/**
	 * Public open call<br>
	 * Will log NPEs and send owner message is out of materials (and owner is
	 * online)
	 * 
	 * @return True on successfully opening the shop
	 */
	public boolean tryOpen(boolean verbose)
	{
		if (sign == null)
			throw new NullPointerException("Sign not set for shop " + id);
		if (modShop)
		{
			open();
			return true;
		}
		if (chest == null)
			throw new NullPointerException("Chest not set for shop " + id);
		try
		{
			if (plugin.isSpecialSign(selling)
					|| getInInventory(chest.getInventory(), getMaterial(selling), getDurability(selling)) >= getAmount(selling))
			{
				open();
				return true;
			}
		}
		catch (Exception e)
		{}
		if (verbose)
			sendOwnerMessage("§cSign with id " + id
					+ " does not have enough materials for another transaction");
		sign.setLine(3, "§cClosed");
		sign.update();
		return false;
	}

	/**
	 * Attempts to send a message to the owner, if they are online
	 * 
	 * @param message
	 *            String
	 * @return True if the message was sent
	 */
	private boolean sendOwnerMessage(String message)
	{
		Player player = plugin.getServer().getPlayer(owner);
		if (player != null && player.isOnline())
		{
			player.sendMessage(message);
			return true;
		}
		return false;
	}

	/**
	 * Only call after verification
	 */
	private void open()
	{
		String[] lines = sign.getLines();
		if (lines[0].equals("") && lines[1].equals("") && lines[2].equals(""))
		{
			sign.setLine(0, "B: " + buying);
			sign.setLine(1, "S: " + selling);
		}
		sign.setLine(3, "§aOpen");
		sign.update();
		open = true;
	}

	/**
	 * Public call
	 */
	public void close()
	{
		sign.setLine(3, "§cClosed");
		sign.update();
		open = false;
	}

	/**
	 * Show the shop information to the player
	 * 
	 * @param player
	 *            Player
	 */
	@SuppressWarnings("boxing")
	public void showInterface(Player player)
	{
		if (!open)
			player.sendMessage("§cThis shop is closed");

		// vars
		String take = "";
		String give = "";

		// buying -> take
		if (buying.equals("0-0"))
			take = "nothing";
		else if (DarkShops.isInt(buying.split("-")[0]))
			take = String.format("%s%s (%d)", Material.getMaterial(Integer.valueOf(buying.split("-")[0]).intValue()).name().toLowerCase(), hasDurability(buying) ? "."
					+ getDurability(buying) : "", getAmount(buying));
		else
			take = String.format("%s%s (%d)", getMaterial(buying).name().toLowerCase(), hasDurability(buying) ? "."
					+ getDurability(buying) : "", getAmount(buying));

		// selling -> give
		if (selling.equals("0-0"))
			give = "nothing";
		else if (DarkShops.isInt(selling.split("-")[0]))
			give = String.format("%s%s (%d)", Material.getMaterial(Integer.valueOf(selling.split("-")[0]).intValue()).name().toLowerCase(), hasDurability(selling) ? "."
					+ getDurability(selling) : "", getAmount(selling));
		else
			give = String.format("%s%s (%d)", selling.contains(":") ? selling.split("-")[0] : getMaterial(selling).name().toLowerCase(), hasDurability(selling) ? "."
					+ getDurability(selling) : "", getAmount(selling));

		// output all of the information
		player.sendMessage(String.format("§7Sign shop §6%d\n§7You pay §6%s\n§7You receive §6%s", id, take, give));
	}

	/**
	 * @param player
	 *            Player
	 * @return True on a successful transaction
	 */
	@SuppressWarnings({ "boxing", "null" })
	public boolean transact(Player player, int times)
	{
		if (!open)
		{
			player.sendMessage("§7This shop, §6" + id + " §7is §cclosed");
			return false;
		}
		Material take = getMaterial(buying);
		int takeAmount = getAmount(buying) * times;
		int takeDurability = getDurability(buying);
		Material give = !selling.contains(":") ? getMaterial(selling) : null;
		int giveAmount = getAmount(selling) * times;
		int giveDurability = !selling.contains(":") ? getDurability(selling) : 0;
		String special = null;
		if (selling.contains("-") && selling.contains(":"))
			special = selling.split("-")[0].split(":")[1];
		boolean takeFromChest = true;
		if (modShop)
			takeFromChest = false;
		if (!take.equals(Material.AIR)
				&& getInInventory(player.getInventory(), take, takeDurability) < takeAmount)
		{
			player.sendMessage(String.format("§7You do not have enough §6%s§7. Need: §6%d§7, have: §6%d", take.name(), takeAmount, getInInventory(player.getInventory(), take, takeDurability)));
			return false;
		}
		if (give != null
				&& chest != null
				&& !give.equals(Material.AIR)
				&& getInInventory(chest.getInventory(), give, giveDurability) < giveAmount)
		{
			player.sendMessage(String.format("§7The chest does not have enough §6%s§7 for §6"
					+ times + " §7transactions", give.name()));
			return false;
		}
		if (plugin.isSpecialSign(selling))
			takeFromChest = false;
		if (selling.startsWith("p:"))
		{
			if (player.hasPermission(special))
			{
				player.sendMessage("§cYou already have that permission node!");
				return false;
			}
			Permission p = Permission.loadFromString(special);
			ApiLayer.addPermission("world", CalculableType.USER, player.getName(), p);
			player.sendMessage("§7Permission node §6" + p.name()
					+ " §7added to your permissions!");
		}
		else if (selling.startsWith("w:"))
		{
			player.sendMessage("§dWarping with Essentials to " + special);
			plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), String.format("warp %s %s", special, player.getName()));
		}
		else if (selling.startsWith("e:"))
		{
			if (special.equals("shops"))
			{
				plugin.incrementShopLimit(player.getName(), times);
				player.sendMessage(String.format("§aShop limit incremented!\n§7Your limit is now §6%d§7 of which you are using §6%d", plugin.getShopLimit(player.getName()), plugin.getOwnedShops(player.getName()).size()));
			}
			else if (special.equals("plots"))
			{
				plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "darkland increase "
						+ player.getName() + " " + times);
				player.sendMessage("§aLandowner plot credit granted!");
			}
			else
			{
				player.sendMessage("§cEvent '" + special
						+ "' not recognized! Cancelling this transaction...");
				return false;
			}
		}
		else if (selling.startsWith("m:"))
		{
			special = special.replace("&p", player.getName()).replace("&i", String.valueOf(id)).replace("&t", String.valueOf(times)).replace("_", " ");
			plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "pe open [SHOP] "
					+ special);
			player.sendMessage("§aPetition opened!");
		}
		else if (selling.startsWith("b:"))
		{
			ItemStack ret = new ItemStack(Material.ENCHANTED_BOOK, 1);
			EnchantmentStorageMeta meta = (EnchantmentStorageMeta) ret.getItemMeta();
			if (DarkShops.getEnchantment(special) == null)
			{
				player.sendMessage("§cInvalid enchantment modifier for this shop.");
				close();
				return false;
			}
			meta.addStoredEnchant(DarkShops.getEnchantment(special), Integer.valueOf(selling.split("-")[0].split(":")[2]).intValue(), true);
			ret.setItemMeta(meta);
			ret.setAmount(giveAmount);
			player.getInventory().addItem(ret);
		}
		else if (selling.startsWith("k:"))
		{
			if (plugin.getConfig().getStringList("kits." + special) == null)
			{
				player.sendMessage("No kit " + special + " defined!");
				return false;
			}
			for (String str : plugin.getConfig().getStringList("kits."
					+ special))
				player.getInventory().addItem(new ItemStack(Integer.valueOf(str.split("-")[0]), Integer.valueOf(str.split("-")[1]).intValue()));
		}
		else if (selling.startsWith("c:"))
		{
			plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), special.replace("_", " ").replace("&p", player.getName()));
			player.sendMessage("§aCommand ran!");
		}
		else if (selling.startsWith("d:"))
		{
			ApiLayer.setValue("world", CalculableType.USER, player.getName(), "prefix", special);
			player.sendMessage("§aChat prefix set!");
		}
		else
		{
			ItemStack giveItemStack = new ItemStack(give, giveAmount);
			giveItemStack.setDurability((short) giveDurability);
			player.getInventory().addItem(giveItemStack);
		}
		takeItems(player.getInventory(), take.getId(), takeAmount, takeDurability);
		if (!modShop)
			chest.getInventory().addItem(new ItemStack(take, takeAmount));
		plugin.logTransaction(this, player, times);
		player.sendMessage("§aTransaction complete!");
		if (takeFromChest)
		{
			if (give == null)
			{
				plugin.getLogger().info("Shop "
						+ id
						+ " could not take items from the chest, even though the boolean was true");
				return false;
			}
			takeItems(chest.getInventory(), give.getId(), giveAmount, giveDurability);
			if (getInInventory(chest.getInventory(), give, giveDurability) < giveAmount)
				close();
		}
		if (chest != null)
			chest.update();
		return true;
	}

	/**
	 * Returns the space in the inventory for the item id
	 * 
	 * @param contents
	 *            ItemStack[]
	 * @param typeId
	 *            int
	 * @return int
	 */
	public int getSpace(ItemStack[] contents, int typeId)
	{
		int count = 0;
		ItemStack addItem = new ItemStack(typeId, 1);
		int maxSize = addItem.getMaxStackSize();
		for (ItemStack i : contents)
		{
			if (i == null || i.getType() == Material.AIR)
			{
				count += maxSize;
				continue;
			}
			if (i.getTypeId() == typeId && i.getAmount() < i.getMaxStackSize())
				count += maxSize - i.getAmount();
		}
		return count;
	}

	/**
	 * Gets the material from a shop-formatted string
	 * 
	 * @param string
	 *            String
	 * @return Material
	 */
	private Material getMaterial(String string)
	{
		String mat = string.split("-")[0];
		if (mat.contains("."))
			mat = mat.split("\\.")[0];
		if (DarkShops.isInt(mat))
			return Material.getMaterial(Integer.valueOf(mat).intValue());
		return Material.valueOf(mat);
	}

	/**
	 * Gets the amount from a shop-formatted string
	 * 
	 * @param string
	 *            String
	 * @return int
	 */
	private int getAmount(String string)
	{
		try
		{
			return Integer.valueOf(string.split("-")[1]).intValue();
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
			return -1;
		}
	}

	/**
	 * Gets the durability from a shop-formatted string
	 * 
	 * @param string
	 *            String
	 * @return int
	 */
	private int getDurability(String string)
	{
		if (string.equals("0-0"))
			return 0;
		try
		{
			String dur = string.split("-")[0];
			if (dur.contains("."))
				return Integer.valueOf(dur.split("\\.")[1]).intValue();
			return 0;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
		catch (IndexOutOfBoundsException e)
		{
			return 0;
		}
	}

	/**
	 * 
	 * @param string
	 *            String
	 * @return True if the formatted string denotes an item with durability
	 */
	private boolean hasDurability(String string)
	{
		return string.contains(".");
	}

	/**
	 * No regards to the durability of the item
	 * 
	 * @param inventory
	 *            Inventory
	 * @param material
	 *            Material
	 * @return int
	 */
	public static int getInInventory(Inventory inventory, Material material)
	{
		int count = 0;
		int id = material.getId();
		if (inventory.getContents().length == 0)
			return 0;
		for (ItemStack i : inventory.getContents())
		{
			if (i == null)
				continue;
			if (i.getTypeId() == id)
			{
				if (DarkShops.isTool(material))
				{
					if (i.getDurability() == 0)
						count += i.getAmount();
				}
				else
					count += i.getAmount();
			}
		}
		return count;
	}

	/**
	 * Goes by the durability of the item if it's not a tool
	 * 
	 * @param inventory
	 *            Inventory
	 * @param material
	 *            Material
	 * @param durability
	 *            int
	 * @return int
	 */
	public static int getInInventory(Inventory inventory, Material material, int durability)
	{
		int count = 0;
		int id = material.getId();
		if (inventory.getContents().length == 0)
			return 0;
		for (ItemStack i : inventory.getContents())
		{
			if (i == null)
				continue;
			if (i.getTypeId() == id)
			{
				if (DarkShops.isTool(material))
				{
					if (i.getDurability() == 0)
						count += i.getAmount();
				}
				else if (i.getDurability() == durability)
					count += i.getAmount();
			}
		}
		return count;
	}

	/**
	 * Takes the items from the inventory
	 * 
	 * @param inventory
	 *            Inventory
	 * @param typeId
	 *            int
	 * @param amount
	 *            int
	 * @param durability
	 *            int
	 */
	public void takeItems(Inventory inventory, int typeId, int amount, int durability)
	{
		if (typeId == 0)
			return;
		int count = 0;
		int smallestSlot = findSmallest(inventory, typeId, durability);
		if (smallestSlot != -1)
		{
			while (count < amount)
			{
				smallestSlot = findSmallest(inventory, typeId, durability);
				if (smallestSlot == -1)
					break;
				ItemStack item = inventory.getItem(smallestSlot);
				if (item != null && item.getTypeId() == typeId
						&& item.getDurability() == durability)
				{
					if (item.getAmount() > amount - count)
					{
						item.setAmount(item.getAmount() - (amount - count));
						inventory.setItem(smallestSlot, item);
						break;
					}
					count += item.getAmount();
					inventory.clear(smallestSlot);
				}
			}
		}
		else
			plugin.getLogger().info("Could not find slot with item");
	}

	/**
	 * Returns the slot number with the smallest count of the item number
	 * 
	 * @param inven
	 *            Inventory
	 * @param typeId
	 *            int
	 * @param durability
	 *            int
	 * @return int
	 */
	@SuppressWarnings("rawtypes")
	public int findSmallest(Inventory inven, int typeId, int durability)
	{
		HashMap<Integer, ? extends ItemStack> items = inven.all(typeId);
		int slot = -1;
		int smallest = 64;
		Set<?> set = items.entrySet();
		Iterator<?> i = set.iterator();
		while (i.hasNext())
		{
			Map.Entry me = (Map.Entry) i.next();
			ItemStack item1 = (ItemStack) me.getValue();
			if (item1.getDurability() != durability)
				continue;
			if (item1.getAmount() <= smallest)
			{
				smallest = item1.getAmount();
				slot = ((Integer) me.getKey()).intValue();
			}
		}
		return slot;
	}

	/**
	 * Show records to the player
	 * 
	 * @param player
	 *            Player
	 * @param args
	 *            String[]
	 */
	public void showRecords(Player player, String[] args)
	{
		player.sendMessage("§7To request shop records, open a petition with the shop id number.");
	}

	/*
	 * GET and SET
	 */

	public boolean isOpen()
	{
		return open;
	}

	public CraftSign getSign()
	{
		return sign;
	}

	public void setSign(CraftSign sign)
	{
		this.sign = sign;
	}

	public DarkShops getPlugin()
	{
		return plugin;
	}

	public String getOwner()
	{
		return owner;
	}

	public void setOwner(String owner)
	{
		this.owner = owner;
	}

	public int getId()
	{
		return id;
	}

	public CraftChest getChest()
	{
		return chest;
	}

	public void setChest(CraftChest chest)
	{
		this.chest = chest;
	}

	public String getBuying()
	{
		return buying;
	}

	public void setBuying(String buying)
	{
		this.buying = buying;
	}

	public String getSelling()
	{
		return selling;
	}

	public void setSelling(String selling)
	{
		this.selling = selling;
	}

	public boolean isModShop()
	{
		return modShop;
	}

	public void setModShop(boolean modShop)
	{
		this.modShop = modShop;
	}

}