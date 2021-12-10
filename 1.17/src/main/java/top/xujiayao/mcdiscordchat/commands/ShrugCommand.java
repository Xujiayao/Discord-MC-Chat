package top.xujiayao.mcdiscordchat.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.MessageArgumentType;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.command.argument.MessageArgumentType.getMessage;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * @author Xujiayao
 */
public class ShrugCommand {

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("shrug").then(argument("message", MessageArgumentType.message()).executes(context -> {
			if (context.getSource() != null) {
				ServerCommandSource source = context.getSource();

				if (source.getPlayer() != null) {
					source.getPlayer().networkHandler.onGameMessage(new ChatMessageC2SPacket(getMessage(context, "message").getString() + " ¯\\_(ツ)_/¯"));
				}
			}

			return 0;
		})));

		dispatcher.register(literal("shrug").executes(context -> {
			if (context.getSource() != null) {
				ServerCommandSource source = context.getSource();

				if (source.getPlayer() != null) {
					source.getPlayer().networkHandler.onGameMessage(new ChatMessageC2SPacket("¯\\_(ツ)_/¯"));
				}
			}

			return 0;
		}));
	}
}
