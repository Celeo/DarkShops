package com.darktidegames.darkshops;

import java.io.File;
import java.sql.Date;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_5_R3.block.CraftChest;
import org.bukkit.craftbukkit.v1_5_R3.block.CraftSign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.darktidegames.empyrean.C;
import com.sk89q.worldedit.bukkit.BukkitUtil;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;

/**
 * @author Celeo
 */
public class DarkShops extends JavaPlugin implements Listener
{

	private List<Shop> shops = new ArrayList<Shop>();
	private int nextShopID = 0;
	private Map<Player, ShopAction> pendingActions = new HashMap<Player, ShopAction>();
	private int defaultShopLimit = 3;
	private double maxRangeBetween = 15.0;
	private Logger shopLogger = Logger.getLogger("DarkShops");

	private WorldGuardPlugin wg = null;

	@Override
	public void onEnable()
	{
		getDataFolder().mkdirs();
		if (!new File(getDataFolder(), "config.yml").exists())
			saveDefaultConfig();
		load();
		Plugin test = getServer().getPluginManager().getPlugin("WorldGuard");
		if (test == null)
			getLogger().severe("Could not connect to WorldGuard!");
		else
			wg = (WorldGuardPlugin) test;
		getServer().getPluginManager().registerEvents(this, this);
		setupLogger();
		getCommand("ys").setExecutor(this);
		getCommand("shop").setExecutor(this);
		getCommand("shops").setExecutor(this);
		getCommand("darkshops").setExecutor(this);
		getLogger().info("Enabled");
	}

	private void setupLogger()
	{
		try
		{
			File temp = new File(getDataFolder().getAbsolutePath()
					+ "/shop.log");
			if (!temp.exists())
				temp.createNewFile();
			shopLogger.setUseParentHandlers(false);
			FileHandler handler = new FileHandler(temp.getAbsolutePath(), true);
			handler.setFormatter(new Formatter()
			{
				@Override
				public String format(LogRecord logRecord)
				{
					return new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z").format(new Date(logRecord.getMillis()))
							+ " " + logRecord.getMessage() + "\n";
				}
			});
			shopLogger.addHandler(handler);
		}
		catch (Exception e)
		{
			shopLogger.setUseParentHandlers(true);
			getLogger().severe("An error occured with setting up the shop record logger.");
		}
	}

	@Override
	public void onDisable()
	{
		shopLogger.setUseParentHandlers(true);
		save();
		getLogger().info("Disabled");
	}

	private void load()
	{
		reloadConfig();
		shops.clear();
		nextShopID = getConfig().getInt("settings.nextShopID", 0);
		defaultShopLimit = getConfig().getInt("settings.defaultShopLimit", 3);
		maxRangeBetween = getConfig().getDouble("settings.maxRangeBetween", 15.0);
		Location temp = null;
		int step = 0;
		for (int i = 0; i != nextShopID + 1; i++)
		{
			step = 0;
			String p = "shops." + i + ".";
			if (getConfig().isSet("shops." + i))
			{
				step = 1;
				try
				{
					step = 2;
					Shop shop = new Shop(this, i);
					step = 3;
					shop.setOwner(getConfig().getString(p + "owner"));
					step = 4;
					shop.setBuying(getConfig().getString(p + "buying"));
					step = 5;
					shop.setSelling(getConfig().getString(p + "selling"));
					step = 6;
					shop.setModShop(getConfig().getBoolean(p + "modshop"));
					step = 7;
					if (!shop.isModShop())
					{
						step = 8;
						temp = C.stringToLocation(getConfig().getString(p
								+ "location.chest"));
						shop.setChest(new CraftChest(temp.getBlock()));
					}
					else
					{
						step = 9;
						shop.setChest(null);
					}
					step = 10;
					temp = C.stringToLocation(getConfig().getString(p
							+ "location.sign"));
					step = 11;
					shop.setSign(new CraftSign(temp.getBlock()));
					step = 12;
					addShop(shop);
					step = 13;
				}
				catch (Exception e)
				{
					getLogger().severe("Could not load shop number " + i
							+ " from the configuration. ERROR: "
							+ e.getMessage() + " at step " + step);
				}
			}
		}
	}

	private void save()
	{
		getConfig().set("settings.nextShopID", Integer.valueOf(nextShopID));
		getConfig().set("settings.defaultShopLimit", Integer.valueOf(defaultShopLimit));
		getConfig().set("settings.maxRangeBetween", Double.valueOf(maxRangeBetween));
		for (Shop shop : shops)
		{
			String p = "shops." + shop.getId() + ".";
			getConfig().set(p + "owner", shop.getOwner());
			getConfig().set(p + "buying", shop.getBuying());
			getConfig().set(p + "selling", shop.getSelling());
			getConfig().set(p + "location.chest", shop.getChest() != null ? C.locationToString(shop.getChest().getLocation()) : "");
			getConfig().set(p + "location.sign", C.locationToString(shop.getSign().getLocation()));
			getConfig().set(p + "open", Boolean.valueOf(shop.isOpen()));
			getConfig().set(p + "modshop", Boolean.valueOf(shop.isModShop()));
		}
		saveConfig();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		if (!(sender instanceof Player))
			return false;
		Player player = (Player) sender;
		if (args == null || args.length == 0)
		{
			player.sendMessage("§7Do command §a/help shop");
			return true;
		}
		String param = args[0].toLowerCase();
		if (param.equals("-y"))
		{
			setPending(new ShopAction(player, ShopAction.Action.TRANSACT, args));
			return true;
		}
		if (param.equals("-t"))
		{
			setPending(new ShopAction(player, ShopAction.Action.RECORDS, args));
			return true;
		}
		if (param.equals("-v"))
		{
			if (args.length == 5)
				validateSetup(new ShopAction(player, ShopAction.Action.SETUP, args));
			else if (args.length == 6)
			{
				if (args[5].equals("m"))
					if (!player.hasPermission("signs.mod"))
						player.sendMessage("§cYou do not have permission to make a mod shop");
					else
						validateSetup(new ShopAction(player, ShopAction.Action.SETUP, args));
			}
			else
				player.sendMessage("§c/shop -v buying qty selling qty");
			return true;
		}
		if (param.equals("-c"))
		{
			setPending(new ShopAction(player, ShopAction.Action.CLOSE, args));
			return true;
		}
		if (param.equals("-o"))
		{
			setPending(new ShopAction(player, ShopAction.Action.OPEN, args));
			return true;
		}
		if (param.equals("-r"))
		{
			setPending(new ShopAction(player, ShopAction.Action.REMOVE, args));
			return true;
		}
		if (param.equals("-i"))
		{
			int inuse = 0;
			String locs = "";
			for (Shop shop : shops)
			{
				if (shop.getOwner().equals(player.getName()))
				{
					inuse++;
					if (locs.equals(""))
						locs = shop.getSign().getLocation().getX() + ", "
								+ shop.getSign().getLocation().getY() + ", "
								+ shop.getSign().getLocation().getZ();
					else
						locs += "; " + shop.getSign().getLocation().getX()
								+ ", " + shop.getSign().getLocation().getY()
								+ ", " + shop.getSign().getLocation().getZ();
				}
			}
			player.sendMessage(String.format("§c== DarkShop readout ==\n§7You are using §6%d §7of §6%d §7allowed shops.", Integer.valueOf(inuse), Integer.valueOf(getShopLimit(player.getName()))));
			player.sendMessage("§7Locations: §c" + locs);
			return true;
		}
		if (param.equals("-admin"))
		{
			if (!hasPerms(player, "signs.admin"))
				return true;
			if (args.length < 2)
			{
				player.sendMessage("§c/shop -admin what");
				return true;
			}
			String s = args[1].toLowerCase();
			if (s.equals("r"))
			{
				if (args.length == 3)
				{
					if (args[2].equalsIgnoreCase("-all"))
					{
						getConfig().set("shops", null);
						shops.clear();
						saveConfig();
						player.sendMessage("§aAll shops removed!");
						return true;
					}
					else if (isInt(args[2]))
					{
						int id = Integer.valueOf(args[2]).intValue();
						for (Shop shop : shops)
						{
							if (shop.getId() == id)
							{
								removeShop(shop);
								player.sendMessage("§aRemoved shop with id §9"
										+ id);
								return true;
							}
						}
						player.sendMessage("§cNo shops found with id §9" + id);
						return true;
					}
					else
					{
						String ret = "";
						Iterator<Shop> i = shops.iterator();
						while (i.hasNext())
						{
							Shop check = i.next();
							if (check.getOwner().equalsIgnoreCase(args[2]))
							{
								removeShop(check);
								if (ret.equals(""))
									ret = String.valueOf(check.getId());
								else
									ret += ", " + String.valueOf(check.getId());
							}
						}
						player.sendMessage("§aShop ids removed that were owned by §9"
								+ args[2] + "§a: §9" + ret);
						return true;
					}
				}
				player.sendMessage("§c/shop -admin r [name,id,-all]");
				return true;
			}
			if (s.equals("defaultconfig"))
			{
				if (!shops.isEmpty())
				{
					player.sendMessage("§cWill not load the default configuration while shops exist!");
					return true;
				}
				saveDefaultConfig();
				player.sendMessage("§aLoaded the default config");
				return true;
			}
			if (s.equals("save"))
			{
				save();
				player.sendMessage("§aSaved all to configuration");
				return true;
			}
			if (s.equals("reload"))
			{
				load();
				player.sendMessage("§aReoaded all from configuration");
				return true;
			}
			if (s.equals("range"))
			{
				// shop -admin range [set]
				if (args.length == 2)
					player.sendMessage("§7Max range between sign and chest is §6"
							+ maxRangeBetween);
				else if (args.length == 3)
				{
					if (isInt(args[2]))
					{
						maxRangeBetween = Double.valueOf(args[2]).doubleValue();
						player.sendMessage("§7Max range between sign and chest set to §6"
								+ maxRangeBetween);
					}
					else
						player.sendMessage("§/shop -admin range [set #]");
				}
				return true;
			}
			if (s.equals("limits"))
			{
				if (args.length == 3)
					player.sendMessage(String.format("§7Shop ownership limit for §6%s§7: §6%d", args[2], Integer.valueOf(getShopLimit(player.getName()))));
				else if (args.length == 4)
				{
					setShopLimit(args[2], Integer.valueOf(args[3]).intValue());
					player.sendMessage(String.format("§7Shop ownership limit for §6%s §7set to §6%s", args[2], args[3]));
				}
				else
					player.sendMessage("§c/shop -admin limits [who]\n§c/shop -admin limits [who] [set value #]");
				return true;
			}
		}
		return false;
	}

	public boolean hasPerms(Player player, String node)
	{
		if (!player.hasPermission(node))
		{
			player.sendMessage("§cYou cannot do that");
			return false;
		}
		return true;
	}

	@SuppressWarnings("incomplete-switch")
	@EventHandler
	public void onPlayerCraftSignInteract(PlayerInteractEvent event)
	{
		// vars
		Player player = event.getPlayer();
		String name = player.getName();
		Block clicked = event.getClickedBlock();
		// really nothing we can do here
		if (clicked == null)
			return;
		// if the block is not a sign, we don't want it
		if (!(clicked.getState() instanceof CraftSign
				|| clicked.getType().equals(Material.SIGN)
				|| clicked.getType().equals(Material.WALL_SIGN) || clicked.getType().equals(Material.SIGN_POST)))
			return;
		// fix against food glitch
		if (!event.getAction().equals(Action.LEFT_CLICK_BLOCK))
			return;
		CraftSign sign = (CraftSign) clicked.getState();
		Shop shop = getShop(sign);
		ShopAction action = getPendingAction(player);
		// no shop, other setup method
		if (shop == null)
			return;
		// no pending action, show interface
		if (action == null)
		{
			shop.showInterface(player);
			return;
		}
		String[] args = action.getRawArgs();
		// if here, then they have a pending action
		// keep them from double-dipping
		removePending(player);
		switch (action.getAction())
		{
		// buying from the shop
		case TRANSACT:
			if (args != null && args.length == 2 && isInt(args[1]))
				shop.transact(player, Integer.valueOf(args[1]).intValue());
			else
				shop.transact(player, 1);
			break;
		// viewing records from the shop
		case RECORDS:
			shop.showRecords(player, args);
			break;
		// close the shop
		case CLOSE:
			if (shop.isOpen())
			{
				if (shop.getOwner().equals(name) || isMod(player))
				{
					shop.close();
					player.sendMessage("§cShop closed");
				}
				else
					player.sendMessage("§cYou are not an owner of that sign");
			}
			else
				player.sendMessage("§cThat shop is not open");
			break;
		// open the shop
		case OPEN:
			if (!shop.isOpen())
			{
				if (shop.getOwner().equals(name) || isMod(player))
				{
					if (shop.tryOpen(true))
						player.sendMessage("§aShop opened");
					else
						player.sendMessage("§cAn error occurred in opening the shop!\nCheck the contents of your chest");
				}
				else
					player.sendMessage("§cYou are not an owner of that sign");
			}
			else
				player.sendMessage("§cThat shop is not closed");
			break;
		// remove the shop
		case REMOVE:
			if (shop.getOwner().equals(name) || isMod(player))
			{
				removeShop(shop);
				player.sendMessage("§cShop removed");
			}
			else
				player.sendMessage("§cYou are not an owner of that sign");
			break;
		}
	}

	@EventHandler
	public void onPlayerNormalCraftSignInteract(PlayerInteractEvent event)
	{
		// vars
		Player player = event.getPlayer();
		Block clicked = event.getClickedBlock();
		// really nothing we can do here
		if (clicked == null)
			return;
		// if the block is not a sign, we don't want it
		if (!(clicked.getState() instanceof CraftSign
				|| clicked.getType().equals(Material.SIGN)
				|| clicked.getType().equals(Material.WALL_SIGN) || clicked.getType().equals(Material.SIGN_POST)))
			return;
		CraftSign sign = (CraftSign) clicked.getState();
		Shop shop = getShop(sign);
		ShopAction action = getPendingAction(player);
		// other method - show the shop menu
		if (action == null)
			return;
		// other method - other shop action
		if (!action.getAction().equals(ShopAction.Action.SETUP))
			return;
		// repeat method - invalid sign
		if (shop != null)
		{
			player.sendMessage("§cThat sign already represents a shop");
			return;
		}
		// keep from double-dipping
		removePending(player);
		String[] args = action.getRawArgs();
		if (args.length == 5)
		{
			// check against range issues and theft
			if (!isInRegion(clicked))
			{
				player.sendMessage("§cYou cannot make a sign in an area that is not regioned.");
				return;
			}
			if (!wg.canBuild(player, clicked))
			{
				player.sendMessage("§cYou cannot make a sign in an area wherein you cannot build.");
				return;
			}
			// normal player shop setup
			ShopAction a = new ShopAction(player, ShopAction.Action.CHOOSE_CHEST, action.getRawArgs());
			a.setSign(sign);
			setPending(a);
			player.sendMessage("§aPlease click the chest you wish to use");
		}
		else if (args.length == 6)
		{
			// mod shop setup
			action.setSign(sign);
			createShop(action);
			player.sendMessage("§6Mod §ashop created!");
		}
	}

	public boolean isInRegion(Block block)
	{
		return !wg.getRegionManager(block.getWorld()).getApplicableRegionsIDs(BukkitUtil.toVector(block)).isEmpty();
	}

	@EventHandler
	public void onPlayerCraftChestInteract(PlayerInteractEvent event)
	{
		// vars
		Player player = event.getPlayer();
		Block clicked = event.getClickedBlock();
		// really nothing we can do here
		if (clicked == null)
			return;
		// check for chest block
		if (!(clicked.getState() instanceof CraftChest || clicked.getType().equals(Material.CHEST)))
			return;
		ShopAction action = getPendingAction(player);
		// if no pending action, then they are simply opening it
		if (action == null)
			return;
		// if they are, in fact, choosing the chest
		if (action.getAction().equals(ShopAction.Action.CHOOSE_CHEST))
		{
			// check against range issues and theft
			if (!wg.canBuild(player, clicked))
			{
				player.sendMessage("§cYou cannot tie a shop to a chest wherein you cannot build.");
				removePending(player);
				return;
			}
			// check range of chest from sign
			double dist = action.getSign().getLocation().distance(clicked.getLocation());
			if (dist > maxRangeBetween)
			{
				player.sendMessage("§cYou cannot tie a sign to a chest that is more that "
						+ maxRangeBetween
						+ " blocks apart. That range is "
						+ new DecimalFormat("#.###").format(dist)
						+ " blocks apart");
				removePending(player);
				return;
			}
			// setup that shop!
			action.setChest((CraftChest) clicked.getState());
			createShop(action);
			player.sendMessage("§aShop created!");
			// keep from double-dipping
			removePending(player);
		}
	}

	/**
	 * Creates a shop from the setup ShopAction object, then stores it in memory
	 * 
	 * @param action
	 *            ShopAction
	 */
	public void createShop(ShopAction action)
	{
		String[] args = action.getRawArgs();
		Shop shop = new Shop(this, getNextShopID());
		shop.setOwner(action.getPlayer().getName());
		shop.setBuying(String.format("%s-%s", args[1], args[2]));
		shop.setSelling(String.format("%s-%s", args[3], args[4]));
		shop.setChest(action.getChest());
		shop.setSign(action.getSign());
		if (args.length == 6)
		{
			shop.setModShop(true);
			shop.setOwner("-server");
		}
		addShop(shop);
	}

	/**
	 * <b>Raw</b>: Adds shop to arraylist of shops
	 * 
	 * @param shop
	 *            Shop
	 */
	public void addShop(Shop shop)
	{
		shops.add(shop);
		shop.tryOpen(false);
	}

	/**
	 * 
	 * @param player
	 *            Player
	 * @return True if the player has moderator permissions
	 */
	public static boolean isMod(Player player)
	{
		return player.hasPermission("signs.mod");
	}

	/**
	 * 
	 * @param sign
	 *            CraftSign
	 * @return <b>Shop</b> object of that sign, or null if no shop is tied to
	 *         that sign
	 */
	public Shop getShop(CraftSign sign)
	{
		return getShop(sign.getLocation());
	}

	/**
	 * 
	 * @param location
	 *            Location
	 * @return <b>Shop</b> object at that location, or null if no shop is tied
	 *         to that location
	 */
	public Shop getShop(Location location)
	{
		for (Shop shop : shops)
			if (shop.getSign().getLocation().equals(location))
				return shop;
		return null;
	}

	/**
	 * 
	 * @param sign
	 *            CraftSign
	 * @return True if that sign is part of a shop
	 */
	public boolean isShop(CraftSign sign)
	{
		return getShop(sign.getLocation()) != null;
	}

	public ShopAction getPendingAction(Player player)
	{
		return pendingActions.get(player);
	}

	public void setPending(ShopAction action)
	{
		pendingActions.put(action.getPlayer(), action);
		action.getPlayer().sendMessage(String.format("§aPending action set as %s %s", action.getAction().toString().toLowerCase(), action.getArgs() != null ? action.getArgs() : ""));
	}

	/**
	 * Checks String[] args validation for shop setup from input command stored
	 * in a ShopAction object
	 * 
	 * @param action
	 *            ShopAction
	 */
	public void validateSetup(ShopAction action)
	{
		List<String> errors = new ArrayList<String>();
		String[] args = action.getRawArgs();
		if (!action.getAction().equals(ShopAction.Action.SETUP))
			errors.add("§cWhoa! The shops plugin is trying to validate a setup string for you, but you're not shown as trying to setup a shop!");
		if (args.length != 6
				&& getOwnedShops(action.getPlayer().getName()).size() == getShopLimit(action.getPlayer().getName()))
			errors.add("§cYou cannot make any more shops. You are at your limit of "
					+ getOwnedShops(action.getPlayer().getName()).size());
		if ((isSpecialSign(args[2]) || isSpecialSign(args[4]))
				&& !action.getPlayer().hasPermission("signs.mod"))
			errors.add("§cYou do not have permission to make special event signs");
		if (!isInt(args[2]) || !isInt(args[4]))
			errors.add("§cOne of the amounts specified is not a number");
		if (errors.isEmpty())
			setPending(action);
		else
			for (String s : errors)
				action.getPlayer().sendMessage(s);
	}

	public boolean isSpecialSign(String string)
	{
		return string.startsWith("e:") || string.startsWith("w:")
				|| string.startsWith("p:") || string.startsWith("m:")
				|| string.startsWith("b:") || string.startsWith("k:")
				|| string.startsWith("c:") || string.startsWith("d:");
	}

	/**
	 * 
	 * @param playerName
	 *            String
	 * @return List of Shop objects owned by that player
	 */
	public List<Shop> getOwnedShops(String playerName)
	{
		List<Shop> ret = new ArrayList<Shop>();
		for (Shop shop : shops)
			if (shop.getOwner().equals(playerName))
				ret.add(shop);
		return ret;
	}

	/**
	 * 
	 * @param playerName
	 *            String
	 * @return int value for the shop owning limit for the passed player
	 */
	public int getShopLimit(String playerName)
	{
		if (playerName.equals("-server"))
			return 90000;
		if (getConfig().isSet("limits." + playerName))
			return getConfig().getInt("limits." + playerName);
		getConfig().set("limits." + playerName, Integer.valueOf(defaultShopLimit));
		return defaultShopLimit;
	}

	/**
	 * Increments the amount of shops the player can own by 1
	 * 
	 * @param playerName
	 *            String
	 */
	public void incrementShopLimit(String playerName)
	{
		if (getConfig().isSet("limits." + playerName))
			getConfig().set("limits." + playerName, Integer.valueOf(getConfig().getInt("limits."
					+ playerName) + 1));
		else
			getConfig().set("limits." + playerName, Integer.valueOf(defaultShopLimit + 1));
	}

	/**
	 * Increments the amount of shops the player can own by the amount
	 * 
	 * @param playerName
	 *            String
	 * @param amount
	 *            int
	 */
	public void incrementShopLimit(String playerName, int amount)
	{
		if (getConfig().isSet("limits." + playerName))
			getConfig().set("limits." + playerName, Integer.valueOf(getConfig().getInt("limits."
					+ playerName)
					+ amount));
		else
			getConfig().set("limits." + playerName, Integer.valueOf(defaultShopLimit
					+ amount));
	}

	/**
	 * Sets the amount of shops the player can own to the amount
	 * 
	 * @param playerName
	 *            String
	 * @param amount
	 *            int
	 */
	public void setShopLimit(String playerName, int amount)
	{
		getConfig().set("limits." + playerName, Integer.valueOf(amount));
		saveConfig();
	}

	/**
	 * 
	 * @param string
	 *            String
	 * @return True if the passed string can be cast to an integer
	 */
	public static boolean isInt(String string)
	{
		try
		{
			Integer.valueOf(string);
			return true;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	public void removePending(Player player)
	{
		pendingActions.put(player, null);
	}

	/**
	 * Writes down the purchase data to the logger file
	 * 
	 * @param shop
	 *            Shop
	 * @param player
	 *            Player
	 * @param given
	 *            String
	 * @param amount
	 *            int
	 */
	@SuppressWarnings("boxing")
	public void logTransaction(Shop shop, Player player, int times)
	{
		shopLogger.info(String.format("[Shop #%d] - %s did %s for %s (%d times)", shop.getId(), player.getName(), shop.getBuying(), shop.getSelling(), times));
	}

	/**
	 * Returns all shops in memory
	 * 
	 * @return <b>List</b> of Shop objects
	 */
	public List<Shop> getShops()
	{
		return shops;
	}

	/**
	 * Removes the shop from the arraylist, preventing it being being accessed
	 * and saved
	 * 
	 * @param shop
	 *            Shop
	 */
	public void removeShop(Shop shop)
	{
		// remove from memory
		shops.remove(shop);
		// remove from storage
		getConfig().set("shops." + shop.getId(), null);
		// remove from sign
		shop.getSign().setLine(3, "");
		// I really don't like this call
		shop.getSign().update();
	}

	/**
	 * 
	 * @param player
	 *            Player
	 * @param shop
	 *            Shop
	 * @return True if the player is the owner of that shop
	 */
	public boolean isOwner(Player player, Shop shop)
	{
		return player.getName().equals(shop.getOwner());
	}

	public static Enchantment getEnchantment(String name)
	{
		name = name.toLowerCase();
		if (name.toLowerCase().equalsIgnoreCase("fire_protection"))
			return Enchantment.PROTECTION_FIRE;
		if (name.toLowerCase().equalsIgnoreCase("blast_protection"))
			return Enchantment.PROTECTION_EXPLOSIONS;
		if (name.toLowerCase().equalsIgnoreCase("projectile_protection"))
			return Enchantment.PROTECTION_PROJECTILE;
		if (name.toLowerCase().equalsIgnoreCase("protection"))
			return Enchantment.PROTECTION_ENVIRONMENTAL;
		if (name.toLowerCase().equalsIgnoreCase("feather_falling"))
			return Enchantment.PROTECTION_FALL;
		if (name.toLowerCase().equalsIgnoreCase("respiration"))
			return Enchantment.OXYGEN;
		if (name.toLowerCase().equalsIgnoreCase("aqua_affinity"))
			return Enchantment.WATER_WORKER;
		if (name.toLowerCase().equalsIgnoreCase("sharpness"))
			return Enchantment.DAMAGE_ALL;
		if (name.toLowerCase().equalsIgnoreCase("smite"))
			return Enchantment.DAMAGE_UNDEAD;
		if (name.toLowerCase().equalsIgnoreCase("bane_of_arthropods"))
			return Enchantment.DAMAGE_ARTHROPODS;
		if (name.toLowerCase().equalsIgnoreCase("knockback"))
			return Enchantment.KNOCKBACK;
		if (name.toLowerCase().equalsIgnoreCase("fire_aspect"))
			return Enchantment.FIRE_ASPECT;
		if (name.toLowerCase().equalsIgnoreCase("looting"))
			return Enchantment.LOOT_BONUS_MOBS;
		if (name.toLowerCase().equalsIgnoreCase("power"))
			return Enchantment.ARROW_DAMAGE;
		if (name.toLowerCase().equalsIgnoreCase("punch"))
			return Enchantment.ARROW_KNOCKBACK;
		if (name.toLowerCase().equalsIgnoreCase("flame"))
			return Enchantment.ARROW_FIRE;
		if (name.toLowerCase().equalsIgnoreCase("infinity"))
			return Enchantment.ARROW_INFINITE;
		if (name.toLowerCase().equalsIgnoreCase("efficiency"))
			return Enchantment.DIG_SPEED;
		if (name.toLowerCase().equalsIgnoreCase("unbreaking"))
			return Enchantment.DURABILITY;
		if (name.toLowerCase().equalsIgnoreCase("silk_touch"))
			return Enchantment.SILK_TOUCH;
		if (name.toLowerCase().equalsIgnoreCase("fortune"))
			return Enchantment.LOOT_BONUS_BLOCKS;
		if (name.toLowerCase().equalsIgnoreCase("thorns"))
			return Enchantment.THORNS;
		return null;
	}

	/**
	 * Increments and then returns the new id for a new shop
	 * 
	 * @return int value for the next shop's id
	 */
	public int getNextShopID()
	{
		nextShopID++;
		return nextShopID;
	}

	@SuppressWarnings("incomplete-switch")
	public static boolean isTool(Material material)
	{
		switch (material)
		{
		case BOW:
			return true;
		case CHAINMAIL_BOOTS:
			return true;
		case CHAINMAIL_CHESTPLATE:
			return true;
		case CHAINMAIL_HELMET:
			return true;
		case CHAINMAIL_LEGGINGS:
			return true;
		case DIAMOND_AXE:
			return true;
		case DIAMOND_BOOTS:
			return true;
		case DIAMOND_CHESTPLATE:
			return true;
		case DIAMOND_HELMET:
			return true;
		case DIAMOND_HOE:
			return true;
		case DIAMOND_LEGGINGS:
			return true;
		case DIAMOND_PICKAXE:
			return true;
		case DIAMOND_SPADE:
			return true;
		case DIAMOND_SWORD:
			return true;
		case GOLD_AXE:
			return true;
		case GOLD_BOOTS:
			return true;
		case GOLD_CHESTPLATE:
			return true;
		case GOLD_HELMET:
			return true;
		case GOLD_HOE:
			return true;
		case GOLD_LEGGINGS:
			return true;
		case GOLD_PICKAXE:
			return true;
		case GOLD_SPADE:
			return true;
		case GOLD_SWORD:
			return true;
		case IRON_AXE:
			return true;
		case IRON_BOOTS:
			return true;
		case IRON_CHESTPLATE:
			return true;
		case IRON_HELMET:
			return true;
		case IRON_HOE:
			return true;
		case IRON_LEGGINGS:
			return true;
		case IRON_PICKAXE:
			return true;
		case IRON_SPADE:
			return true;
		case IRON_SWORD:
			return true;
		case LEATHER_BOOTS:
			return true;
		case LEATHER_CHESTPLATE:
			return true;
		case LEATHER_HELMET:
			return true;
		case LEATHER_LEGGINGS:
			return true;
		case WOOD_HOE:
			return true;
		case WOOD_PICKAXE:
			return true;
		case WOOD_PLATE:
			return true;
		case WOOD_SPADE:
			return true;
		case WOOD_SWORD:
			return true;
		}
		return false;
	}

	public WorldGuardPlugin getWG()
	{
		return wg;
	}

}