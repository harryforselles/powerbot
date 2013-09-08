package org.powerbot.script.methods;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.powerbot.script.lang.Filter;
import org.powerbot.script.lang.ItemQuery;
import org.powerbot.script.util.Condition;
import org.powerbot.script.util.Random;
import org.powerbot.script.wrappers.Component;
import org.powerbot.script.wrappers.GameObject;
import org.powerbot.script.wrappers.Interactive;
import org.powerbot.script.wrappers.Item;
import org.powerbot.script.wrappers.Locatable;
import org.powerbot.script.wrappers.Npc;
import org.powerbot.script.wrappers.Tile;
import org.powerbot.script.wrappers.Widget;

import static org.powerbot.script.util.Constants.getInt;
import static org.powerbot.script.util.Constants.getIntA;
import static org.powerbot.script.util.Constants.getObj;

/**
 * Utilities pertaining to the bank.
 *
 * @author Timer
 */
public class Bank extends ItemQuery<Item> {
	public static final int[] BANK_NPC_IDS = getIntA("bank.npc.ids");
	public static final int[] BANK_BOOTH_IDS = getIntA("bank.booth.ids");
	public static final int[] BANK_COUNTER_IDS = getIntA("bank.counter.ids");
	public static final int[] BANK_CHEST_IDS = getIntA("bank.chest.ids");
	public static final Tile[] UNREACHABLE_BANK_TILES = getObj("bank.unreachable.tiles", Tile[].class);

	private static final Filter<Interactive> UNREACHABLE_FILTER = new Filter<Interactive>() {
		@Override
		public boolean accept(Interactive interactive) {
			if (interactive instanceof Locatable) {
				Tile tile = ((Locatable) interactive).getLocation();
				for (Tile bad : UNREACHABLE_BANK_TILES) {
					if (tile.equals(bad)) {
						return false;
					}
				}
			}
			return true;
		}
	};

	public static final int WIDGET = getInt("bank.widget");
	public static final int COMPONENT_BUTTON_CLOSE = getInt("bank.component.button.close");
	public static final int COMPONENT_CONTAINER_ITEMS = getInt("bank.component.container.items");
	public static final int COMPONENT_BUTTON_WITHDRAW_MODE = getInt("bank.component.button.withdraw.mode");
	public static final int COMPONENT_BUTTON_DEPOSIT_INVENTORY = getInt("bank.component.button.deposit.inventory");
	public static final int COMPONENT_BUTTON_DEPOSIT_MONEY = getInt("bank.component.button.deposit.money");
	public static final int COMPONENT_BUTTON_DEPOSIT_EQUIPMENT = getInt("bank.component.button.deposit.equipment");
	public static final int COMPONENT_BUTTON_DEPOSIT_FAMILIAR = getInt("bank.component.button.deposit.familiar");
	public static final int COMPONENT_SCROLL_BAR = getInt("bank.component.scroll.bar");
	public static final int SETTING_BANK_STATE = getInt("bank.setting.bank.state");
	public static final int SETTING_WITHDRAW_MODE = getInt("bank.setting.withdraw.mode");

	public Bank(MethodContext factory) {
		super(factory);
	}

	private Interactive getBank() {
		Filter<Interactive> f = new Filter<Interactive>() {
			@Override
			public boolean accept(final Interactive interactive) {
				return interactive.isOnScreen();
			}
		};

		List<Interactive> interactives = new ArrayList<>();
		ctx.npcs.select().id(BANK_NPC_IDS).select(f).select(UNREACHABLE_FILTER).nearest().limit(3).shuffle().limit(1).addTo(interactives);
		List<GameObject> cache = new ArrayList<>();
		ctx.objects.select().addTo(cache);
		ctx.objects.id(BANK_BOOTH_IDS).select(f).select(UNREACHABLE_FILTER).nearest().limit(3).shuffle().limit(1).addTo(interactives);
		ctx.objects.select(cache).id(BANK_COUNTER_IDS).select(f).select(UNREACHABLE_FILTER).nearest().limit(3).shuffle().limit(1).addTo(interactives);
		ctx.objects.select(cache).id(BANK_CHEST_IDS).select(f).select(UNREACHABLE_FILTER).nearest().limit(3).shuffle().limit(1).addTo(interactives);

		if (interactives.isEmpty()) {
			return ctx.objects.getNil();
		}

		return interactives.get(Random.nextInt(0, interactives.size()));
	}

	/**
	 * Returns the absolute nearest bank for walking purposes. Do not use this to open the bank.
	 *
	 * @return the {@link Locatable} of the nearest bank or {@link Tile#NIL}
	 * @see #open()
	 */
	public Locatable getNearest() {
		Locatable nearest = ctx.npcs.select().select(UNREACHABLE_FILTER).id(BANK_NPC_IDS).nearest().limit(1).poll();

		Tile loc = ctx.players.local().getLocation();
		for (GameObject object : ctx.objects.select().select(UNREACHABLE_FILTER).
				id(BANK_BOOTH_IDS, BANK_COUNTER_IDS, BANK_CHEST_IDS).nearest().limit(1)) {
			if (loc.distanceTo(object) < loc.distanceTo(nearest)) {
				nearest = object;
			}
		}
		return nearest;
	}

	/**
	 * Determines if a bank is present in the loaded region.
	 *
	 * @return <tt>true</tt> if a bank is present; otherwise <tt>false</tt>
	 */
	public boolean isPresent() {
		return getNearest() != Tile.NIL;
	}

	/**
	 * Determines if a bank is on screen.
	 *
	 * @return <tt>true</tt> if a bank is in view; otherwise <tt>false</tt>
	 */
	public boolean isOnScreen() {
		return getBank().isValid();
	}

	/**
	 * Determines if the bank is open.
	 *
	 * @return <tt>true</tt> is the bank is open; otherwise <tt>false</tt>
	 */
	public boolean isOpen() {
		return ctx.widgets.get(WIDGET, COMPONENT_CONTAINER_ITEMS).isValid();
	}

	/**
	 * Opens a random on-screen bank.
	 * <p/>
	 * Do not continue execution within the current poll after this method so BankPin may activate.
	 *
	 * @return <tt>true</tt> if the bank was opened; otherwise <tt>false</tt>
	 */
	public boolean open() {
		if (isOpen()) {
			return true;
		}
		Interactive interactive = getBank();
		final int id;
		if (interactive.isValid()) {
			if (interactive instanceof Npc) {
				id = ((Npc) interactive).getId();
			} else if (interactive instanceof GameObject) {
				id = ((GameObject) interactive).getId();
			} else {
				id = -1;
			}
		} else {
			id = -1;
		}
		if (id == -1) {
			return false;
		}
		int index = -1;
		final int[][] ids = {BANK_NPC_IDS, BANK_BOOTH_IDS, BANK_CHEST_IDS, BANK_COUNTER_IDS};
		for (int i = 0; i < ids.length; i++) {
			Arrays.sort(ids[i]);
			if (Arrays.binarySearch(ids[i], id) >= 0) {
				index = i;
				break;
			}
		}
		if (index == -1) {
			return false;
		}
		final String[] actions = {"Bank", "Bank", null, "Bank"};
		final String[] options = {null, "Bank booth", null, "Counter"};
		if (actions[index] == null) {
			if (interactive.hover()) {
				sleep(50, 100);
			}
			actions[index] = ctx.menu.indexOf(Menu.filter("Open")) != -1 ? "Open" : ctx.menu.indexOf(Menu.filter("Use")) != -1 ? "Use" : null;
			if (actions[index] == null) {
				return false;
			}
		}
		if (interactive.interact(actions[index], options[index])) {
			final Widget bankPin = ctx.widgets.get(13);
			for (int i = 0; i < 20 && !isOpen() && !bankPin.isValid(); i++) {
				sleep(200, 300);
			}
		}
		return isOpen();
	}

	/**
	 * Closes the bank by means of walking or the 'X'.
	 *
	 * @param walk <tt>true</tt> to close by walking (random), <tt>false</tt> to close by the 'X'.
	 * @return <tt>true</tt> if the bank was closed; otherwise <tt>false</tt>
	 */
	public boolean close(boolean walk) {
		if (!isOpen()) {
			return true;
		}
		Tile t = ctx.players.local().getLocation().derive(Random.nextInt(-5, 5), Random.nextInt(-5, 5));
		Component c = ctx.widgets.get(WIDGET, COMPONENT_BUTTON_CLOSE);
		if (walk && Random.nextBoolean() ? ctx.movement.stepTowards(t) : !c.interact("Close")) {
			return !isOpen();
		}

		return Condition.wait(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return !isOpen();
			}
		}, Random.nextInt(100, 200), 10);
	}

	/**
	 * Closes the bank by walking or clicking the 'X'.
	 *
	 * @return <tt>true</tt> if the bank was closed; otherwise <tt>false</tt>
	 */
	public boolean close() {
		return close(true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected List<Item> get() {
		final Component c = ctx.widgets.get(WIDGET, COMPONENT_CONTAINER_ITEMS);
		if (c == null || !c.isValid()) {
			return new ArrayList<>();
		}
		final Component[] components = c.getChildren();
		List<Item> items = new ArrayList<>(components.length);
		for (final Component i : components) {
			if (i.getItemId() != -1) {
				items.add(new Item(ctx, i));
			}
		}
		return items;
	}

	/**
	 * Grabs the {@link Item} at the provided index.
	 *
	 * @param index the index of the item to grab
	 * @return the {@link Item} at the specified index; or {@link org.powerbot.script.methods.Bank#getNil()}
	 */
	public Item getItemAt(final int index) {
		final Component i = ctx.widgets.get(WIDGET, COMPONENT_CONTAINER_ITEMS).getChild(index);
		if (i.getItemId() != -1) {
			return new Item(ctx, i);
		}
		return getNil();
	}

	/**
	 * Returns the first index of the provided item id.
	 *
	 * @param id the id of the item
	 * @return the index of the item; otherwise {@code -1}
	 */
	public int indexOf(final int id) {
		final Component items = ctx.widgets.get(WIDGET, COMPONENT_CONTAINER_ITEMS);
		if (items == null || !items.isValid()) {
			return -1;
		}
		final Component[] comps = items.getChildren();
		for (int i = 0; i < comps.length; i++) {
			if (comps[i].getItemId() == id) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * @return the index of the current bank tab
	 */
	public int getCurrentTab() {
		return ((ctx.settings.get(SETTING_BANK_STATE) >>> 24) - 136) / 8;
	}

	/**
	 * Changes the current tab to the provided index.
	 *
	 * @param index the index desired
	 * @return <tt>true</tt> if the tab was successfully changed; otherwise <tt>false</tt>
	 */
	public boolean setCurrentTab(final int index) {
		Component c = ctx.widgets.get(WIDGET, 35 - (index * 2));
		return c.click() && Condition.wait(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return getCurrentTab() == index;
			}
		}, 100, 8);
	}

	/**
	 * Returns the item in the specified tab if it exists.
	 *
	 * @param index the tab index
	 * @return the {@link Item} displayed in the tab; otherwise {@link org.powerbot.script.methods.Bank#getNil()}
	 */
	public Item getTabItem(final int index) {
		final Component c = ctx.widgets.get(WIDGET, 82 - (index * 2));
		if (c != null && c.isValid()) {
			return new Item(ctx, c);
		}
		return getNil();
	}

	/**
	 * Withdraws an item with the provided id and amount.
	 *
	 * @param id     the id of the item
	 * @param amount the amount to withdraw
	 * @return <tt>true</tt> if the item was withdrew, does not determine if amount was matched; otherwise <tt>false</tt>
	 */
	public boolean withdraw(int id, Amount amount) {
		return withdraw(id, amount.getValue());
	}

	/**
	 * Withdraws an item with the provided id and amount.
	 *
	 * @param id     the id of the item
	 * @param amount the amount to withdraw
	 * @return <tt>true</tt> if the item was withdrew, does not determine if amount was matched; otherwise <tt>false</tt>
	 */
	public boolean withdraw(int id, int amount) {//TODO: anti pattern
		Item item = select().id(id).poll();
		final Component container = ctx.widgets.get(WIDGET, COMPONENT_CONTAINER_ITEMS);
		if (!item.isValid() || !container.isValid()) {
			return false;
		}

		final Component c = item.getComponent();
		Point p = c.getRelativeLocation();
		if (p.y == 0) {
			for (int i = 0; i < 5 && getCurrentTab() != 0; i++) {
				if (!setCurrentTab(0)) {
					sleep(100, 200);
				}
			}
		}
		if (c.getRelativeLocation().y == 0) {
			return false;
		}
		final Rectangle bounds = container.getViewportRect();
		final Component scroll = ctx.widgets.get(WIDGET, COMPONENT_SCROLL_BAR);
		if (scroll == null || bounds == null) {
			return false;
		}
		if (!bounds.contains(c.getBoundingRect())) {
			if (ctx.widgets.scroll(c, scroll, bounds.contains(ctx.mouse.getLocation()))) {
				sleep(200, 400);
			}
			if (!bounds.contains(c.getBoundingRect())) {
				return false;
			}
		}
		String action = "Withdraw-" + amount;
		if (amount == 0 ||
				(item.getStackSize() <= amount && amount != 1 && amount != 5 && amount != 10)) {
			action = "Withdraw-All";
		} else if (amount == -1 || amount == (item.getStackSize() - 1)) {
			action = "Withdraw-All but one";
		}

		final int inv = ctx.backpack.getMoneyPouch() + ctx.backpack.select().count(true);
		if (containsAction(c, action)) {
			if (!c.interact(action)) {
				return false;
			}
		} else {
			if (!c.interact("Withdraw-X")) {
				return false;
			}
			for (int i = 0; i < 20 && !isInputWidgetOpen(); i++) {
				sleep(100, 200);
			}
			if (!isInputWidgetOpen()) {
				return false;
			}
			sleep(200, 800);
			ctx.keyboard.sendln(amount + "");
		}
		for (int i = 0; i < 25 && ctx.backpack.getMoneyPouch() + ctx.backpack.select().count(true) == inv; i++) {
			sleep(100, 200);
		}
		return ctx.backpack.getMoneyPouch() + ctx.backpack.select().count(true) != inv;
	}

	/**
	 * Deposits an item with the provided id and amount.
	 *
	 * @param id     the id of the item
	 * @param amount the amount to deposit
	 * @return <tt>true</tt> if the item was deposited, does not determine if amount was matched; otherwise <tt>false</tt>
	 */
	public boolean deposit(int id, Amount amount) {
		return deposit(id, amount.getValue());
	}

	/**
	 * Deposits an item with the provided id and amount.
	 *
	 * @param id     the id of the item
	 * @param amount the amount to deposit
	 * @return <tt>true</tt> if the item was deposited, does not determine if amount was matched; otherwise <tt>false</tt>
	 */
	public boolean deposit(final int id, final int amount) {
		Item item = ctx.backpack.select().id(id).shuffle().poll();
		if (!isOpen() || amount < 0 || !item.isValid()) {
			return false;
		}

		String action = "Deposit-" + amount;
		final int c = ctx.backpack.select().id(item.getId()).count(true);
		if (c == 1) {
			action = "Deposit";
		} else if (c <= amount || amount == 0) {
			action = "Deposit-All";
		}

		final Component comp = item.getComponent();
		final int inv = ctx.backpack.select().count(true);
		if (containsAction(comp, action)) {
			if (!comp.interact(action)) {
				return false;
			}
		} else {
			if (!comp.interact("Withdraw-X")) {
				return false;
			}
			for (int i = 0; i < 20 && !isInputWidgetOpen(); i++) {
				sleep(100, 200);
			}
			if (!isInputWidgetOpen()) {
				return false;
			}
			sleep(200, 800);
			ctx.keyboard.sendln(amount + "");
		}
		for (int i = 0; i < 25 && ctx.backpack.select().count(true) == inv; i++) {
			sleep(100, 200);
		}
		return ctx.backpack.select().count(true) != inv;
	}

	/**
	 * Deposits the inventory via the button.
	 *
	 * @return <tt>true</tt> if the button was clicked, not if the inventory is empty; otherwise <tt>false</tt>
	 */
	public boolean depositInventory() {
		return ctx.backpack.select().isEmpty() || ctx.widgets.get(WIDGET, COMPONENT_BUTTON_DEPOSIT_INVENTORY).click();
	}

	/**
	 * Deposits equipment via the button.
	 *
	 * @return <tt>true</tt> if the button was clicked; otherwise <tt>false</tt>
	 */
	public boolean depositEquipment() {
		return ctx.widgets.get(WIDGET, COMPONENT_BUTTON_DEPOSIT_EQUIPMENT).click();
	}

	/**
	 * Deposits familiar inventory via the button.
	 *
	 * @return <tt>true</tt> if the button was clicked; otherwise <tt>false</tt>
	 */
	public boolean depositFamiliar() {
		return ctx.widgets.get(WIDGET, COMPONENT_BUTTON_DEPOSIT_FAMILIAR).click();
	}

	/**
	 * Deposits the money pouch via the button.
	 *
	 * @return <tt>true</tt> if the button was clicked; otherwise <tt>false</tt>
	 */
	public boolean depositMoneyPouch() {
		return ctx.widgets.get(WIDGET, COMPONENT_BUTTON_DEPOSIT_MONEY).click();
	}

	/**
	 * Changes the withdraw mode.
	 *
	 * @param noted <tt>true</tt> for noted items; otherwise <tt>false</tt>
	 * @return <tt>true</tt> if the withdraw mode was successfully changed; otherwise <tt>false</tt>
	 */
	public boolean setWithdrawMode(final boolean noted) {
		if (isWithdrawModeNoted() != noted) {
			final Component c = ctx.widgets.get(WIDGET, COMPONENT_BUTTON_WITHDRAW_MODE);
			if (c != null && c.isValid() && c.click(true)) {
				for (int i = 0; i < 20 && isWithdrawModeNoted() != noted; i++) {
					sleep(100, 200);
				}
			}
		}
		return isWithdrawModeNoted() == noted;
	}

	/**
	 * Determines if the withdraw mode is noted mode.
	 *
	 * @return <tt>true</tt> if withdrawing as notes; otherwise <tt>false</tt>
	 */
	public boolean isWithdrawModeNoted() {
		return ctx.settings.get(SETTING_WITHDRAW_MODE) == 0x1;
	}

	private boolean containsAction(final Component c, String action) {
		action = action.toLowerCase();
		final String[] actions = c.getActions();
		if (action == null) {
			return false;
		}
		for (final String a : actions) {
			if (a != null && a.toLowerCase().contains(action)) {
				return true;
			}
		}
		return false;
	}

	private boolean isInputWidgetOpen() {
		final Component child = ctx.widgets.get(1469, 1);
		return child != null && child.isVisible();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Item getNil() {
		return new Item(ctx, -1, -1, null);
	}

	/**
	 * An enumeration providing standard bank amount options.
	 */
	public static enum Amount {
		ONE(1), FIVE(5), TEN(10), ALL_BUT_ONE(-1), ALL(0);

		private final int value;

		private Amount(final int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
	}
}
