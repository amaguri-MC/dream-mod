package net.amaguri.yume.stage;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Desired block layout of one stage inside an absolute bounding box. Everything inside the box that is not
 * listed becomes air, so applying the same plan again always rebuilds the same stage (idempotent).
 */
final class Plan {
	final int x1, y1, z1, x2, y2, z2;
	private final Map<Long, BlockState> blocks = new HashMap<>();

	Plan(int x1, int y1, int z1, int x2, int y2, int z2) {
		this.x1 = Math.min(x1, x2);
		this.y1 = Math.min(y1, y2);
		this.z1 = Math.min(z1, z2);
		this.x2 = Math.max(x1, x2);
		this.y2 = Math.max(y1, y2);
		this.z2 = Math.max(z1, z2);
	}

	boolean inBox(int x, int y, int z) {
		return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
	}

	void set(int x, int y, int z, BlockState state) {
		if (inBox(x, y, z)) {
			blocks.put(BlockPos.asLong(x, y, z), state);
		}
	}

	void fill(int ax, int ay, int az, int bx, int by, int bz, BlockState state) {
		for (int y = Math.min(ay, by); y <= Math.max(ay, by); y++) {
			for (int z = Math.min(az, bz); z <= Math.max(az, bz); z++) {
				for (int x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) {
					set(x, y, z, state);
				}
			}
		}
	}

	BlockState get(int x, int y, int z) {
		return blocks.get(BlockPos.asLong(x, y, z));
	}

	/** Loads (and force-loads) nothing itself; the chunks must already be loaded. */
	void apply(ServerLevel level) {
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = y1; y <= y2; y++) {
			for (int z = z1; z <= z2; z++) {
				for (int x = x1; x <= x2; x++) {
					BlockState s = blocks.get(BlockPos.asLong(x, y, z));
					p.set(x, y, z);
					level.setBlock(p, s == null ? air : s, Kit.FLAGS);
				}
			}
		}
	}
}
