package dev.alaindustrial.core.drone;

import java.util.Arrays;

/** A binary min-heap of cell indices by their priority, for {@link DroneRoute}'s search; grows as needed. */
final class CellHeap {
	private int[] items;
	private float[] keys;
	private int size;

	CellHeap(int capacity) {
		items = new int[Math.max(16, capacity)];
		keys = new float[items.length];
	}

	boolean isEmpty() {
		return size == 0;
	}

	void push(int item, float key) {
		if (size == items.length) {
			items = Arrays.copyOf(items, size * 2);
			keys = Arrays.copyOf(keys, size * 2);
		}
		int i = size++;
		while (i > 0) {
			int up = (i - 1) >> 1;
			if (keys[up] <= key) {
				break;
			}
			items[i] = items[up];
			keys[i] = keys[up];
			i = up;
		}
		items[i] = item;
		keys[i] = key;
	}

	int pop() {
		int top = items[0];
		int lastItem = items[--size];
		float lastKey = keys[size];
		int i = 0;
		while (true) {
			int child = 2 * i + 1;
			if (child >= size) {
				break;
			}
			if (child + 1 < size && keys[child + 1] < keys[child]) {
				child++;
			}
			if (keys[child] >= lastKey) {
				break;
			}
			items[i] = items[child];
			keys[i] = keys[child];
			i = child;
		}
		items[i] = lastItem;
		keys[i] = lastKey;
		return top;
	}
}
