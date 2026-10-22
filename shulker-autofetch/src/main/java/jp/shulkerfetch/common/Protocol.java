package jp.shulkerfetch.common;

import net.minecraft.util.Identifier;

/** Wire protocol shared by the client and the server half of the mod. */
public final class Protocol {
	public static final String MOD_ID = "shulkerfetch";
	/** Bumped whenever the packet layout changes. Client and server must match exactly. */
	public static final int VERSION = 1;

	/** S2C: sent once on join. Tells the client that the server half is installed. */
	public static final Identifier HELLO = new Identifier(MOD_ID, "hello");
	/** C2S: ask the server to take one stack of an item out of a shulker box. */
	public static final Identifier REQUEST = new Identifier(MOD_ID, "request");
	/** S2C: result of a request. */
	public static final Identifier RESPONSE = new Identifier(MOD_ID, "response");

	public enum Status {
		OK,
		NOT_FOUND,
		INVENTORY_FULL,
		ALREADY_AVAILABLE,
		BUSY,
		DISABLED,
		BAD_REQUEST,
		PROTOCOL_MISMATCH;

		public static Status byId(int id) {
			Status[] all = values();
			return id >= 0 && id < all.length ? all[id] : BAD_REQUEST;
		}
	}

	private Protocol() {
	}
}
