package com.darktidegames.darkshops;

import org.bukkit.craftbukkit.v1_5_R3.block.CraftChest;
import org.bukkit.craftbukkit.v1_5_R3.block.CraftSign;
import org.bukkit.entity.Player;

/**
 * 
 * @author Celeo
 */
public class ShopAction
{

	private final Player player;
	private final Action action;
	private final String[] args;
	private Shop shop;
	private CraftSign sign;
	private CraftChest chest;

	public ShopAction(Player player, Action action, String[] args)
	{
		this.player = player;
		this.action = action;
		this.args = args;
	}

	public ShopAction(Player player, Action action)
	{
		this(player, action, new String[0]);
	}

	public Player getPlayer()
	{
		return player;
	}

	public Action getAction()
	{
		return action;
	}

	public String getArgs()
	{
		switch (action)
		{
		case CLOSE:
			return "";
		case OPEN:
			return "";
		case RECORDS:
			return args.length > 1 ? args[1] : "1";
		case REMOVE:
			return "";
		case SETUP:
			if (args.length == 5)
				return args[1] + ", " + args[2] + ", " + args[3] + ", "
						+ args[4];
			if (args.length == 6)
				return args[1] + ", " + args[2] + ", " + args[3] + ", "
						+ args[4] + ", " + args[5];
			return null;
		case TRANSACT:
			return args.length > 1 ? args[1] : "1";
		case CHOOSE_CHEST:
			return "";
		}
		return "";
	}

	public String[] getRawArgs()
	{
		return args;
	}

	public Shop getShop()
	{
		return shop;
	}

	public void setShop(Shop shop)
	{
		this.shop = shop;
	}

	public CraftChest getChest()
	{
		return chest;
	}

	public void setChest(CraftChest chest)
	{
		this.chest = chest;
	}

	public CraftSign getSign()
	{
		return sign;
	}

	public void setSign(CraftSign sign)
	{
		this.sign = sign;
	}

	public enum Action
	{
		TRANSACT, RECORDS, SETUP, CHOOSE_CHEST, CLOSE, OPEN, REMOVE;
	}

}