package jsettlers.common.images;
import java.util.Arrays;

// DO NOT EDIT THIS FILE, IT IS GENERATED

public final class TextureMap {
	private TextureMap() {}

	public static int getIndex(String name) {
		int arrindex = Arrays.binarySearch(names, name);
		if (arrindex < 0) {
			throw new IllegalArgumentException("Could not find " + name + " in image map.");
		}
		return indexes[arrindex];
	}

	private static final String[] names = new String[] {
		"font.0",
		"font.1",
		"font_grid.0",
		"joinphase.0",
		"lagerhaus.0",
		"ready.0",
		"ready.1",
		"slider.0",
		"startscreen.0",
	};
	private static final int[] indexes = new int[] {
		0,
		1,
		6,
		8,
		5,
		3,
		4,
		2,
		7,
	};
}