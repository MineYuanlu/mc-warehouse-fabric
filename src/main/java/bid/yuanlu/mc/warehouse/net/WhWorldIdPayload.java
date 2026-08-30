package bid.yuanlu.mc.warehouse.net;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import bid.yuanlu.mc.warehouse.YuanluWarehouse;

/**
 * S2C world 身份推送（PDD §4.2/§11）：服务端把存档级 worldId、存档名与
 * 全部维度列表推给客户端。
 * <p>
 * 仅在服务端装有本 mod 时发送（Fabric canSend 按客户端声明 channel 过滤，
 * 原版客户端永不收到）；worldId 缺省 {@code ""}（id 文件不可写等）。
 * v2 起 codec 含扩展字段，与 v1 mod 版本错配时解码失败断连——两端须同版本 mod
 * （universal jar 策略下天然满足）。
 */
public record WhWorldIdPayload(int protocol, String worldId, String levelName,
		List<String> levels) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<WhWorldIdPayload> TYPE =
			new CustomPacketPayload.Type<>(YuanluWarehouse.id("world_id"));

	public static final int PROTOCOL = 2;

	public static final StreamCodec<ByteBuf, WhWorldIdPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, WhWorldIdPayload::protocol,
			ByteBufCodecs.STRING_UTF8, WhWorldIdPayload::worldId,
			ByteBufCodecs.STRING_UTF8, WhWorldIdPayload::levelName,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), WhWorldIdPayload::levels,
			WhWorldIdPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
