//#if MC >= 11900
package top.xujiayao.mcdiscordchat.minecraft.mixins;

import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Mixin;

/**
 * @author Xujiayao
 */
@Mixin(Language.class)
public abstract class MixinLanguage {
}
//#endif