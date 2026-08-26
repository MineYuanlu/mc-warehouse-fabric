package bid.yuanlu.mc.warehouse.test;

import bid.yuanlu.mc.warehouse.core.registry.SelectorCodecs;
import bid.yuanlu.mc.warehouse.impl.allocator.FirstFitAllocator;
import bid.yuanlu.mc.warehouse.impl.quantity.CountSelector;
import bid.yuanlu.mc.warehouse.impl.quantity.FillSlotsSelector;
import bid.yuanlu.mc.warehouse.impl.quantity.GroupSelector;
import bid.yuanlu.mc.warehouse.impl.quantity.PercentSelector;
import bid.yuanlu.mc.warehouse.impl.selector.CompositeSelector;
import bid.yuanlu.mc.warehouse.impl.selector.IdSelector;
import bid.yuanlu.mc.warehouse.impl.selector.NameSelector;
import bid.yuanlu.mc.warehouse.impl.selector.NbtSelector;
import bid.yuanlu.mc.warehouse.impl.selector.TagSelector;

/**
 * 测试辅助：注册全部内置 codec（每次从干净状态全量安装），供序列化往返等测试复用。
 * L11 的正式内置注册落地后可切换为调用生产入口。
 */
public final class TestCodecs {

	public static synchronized void install() {
		SelectorCodecs.resetForTest();
		SelectorCodecs.registerItem(IdSelector.codec());
		SelectorCodecs.registerItem(TagSelector.codec());
		SelectorCodecs.registerItem(NameSelector.codec());
		SelectorCodecs.registerItem(NbtSelector.codec());
		SelectorCodecs.registerItem(CompositeSelector.codec());
		SelectorCodecs.registerQuantity(CountSelector.codec());
		SelectorCodecs.registerQuantity(GroupSelector.codec());
		SelectorCodecs.registerQuantity(FillSlotsSelector.codec());
		SelectorCodecs.registerQuantity(PercentSelector.codec());
	}

	private TestCodecs() {
	}
}

