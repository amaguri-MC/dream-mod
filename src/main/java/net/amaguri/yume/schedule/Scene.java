package net.amaguri.yume.schedule;

/** What a group sees during one dream. CONTRACT FILE. */
public enum Scene {
	/** Sheep jumping a fence; cast.sheepRider rides one. Built in the dream dimension. */
	SHEEP,
	/** cast.miner mines a ceiling diamond, a creeper falls on them. Dream dimension. */
	DIAMOND,
	/** cast.balloon eats too much, swells and floats away. Dream dimension. */
	BALLOON,
	/** All-white void with a pure-white silhouette figure; text "help". Dream dimension. */
	HELP,
	/** Fixed camera "waterfall" from the config (the real End waterfall), loud water sound. */
	WATERFALL,
	/** Fixed camera "room" from the config (the real white room in the End). */
	ROOM,
	/** Trapped group only: watch through the eyes of config.povTarget, then get the stream URL in chat. */
	POV_STREAM,
	/** No dream for this group. */
	NONE
}
