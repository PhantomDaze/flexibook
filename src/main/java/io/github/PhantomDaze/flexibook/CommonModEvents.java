package io.github.PhantomDaze.flexibook;

import io.github.PhantomDaze.flexibook.command.FlexiBookCommands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = FlexiBookMod.MOD_ID)
public final class CommonModEvents {
    private CommonModEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FlexiBookCommands.register(event.getDispatcher());
    }
}
