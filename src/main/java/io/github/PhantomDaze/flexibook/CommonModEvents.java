package io.github.PhantomDaze.flexibook;

import io.github.PhantomDaze.flexibook.command.FlexiBookCommands;

//? if neoforge {
/*import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
*///?} else {
//? if forge {
/*import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*///?}
//?}

//? if neoforge {
/*@EventBusSubscriber(modid = FlexiBookMod.MOD_ID)
*///?} else {
//? if forge {
/*@Mod.EventBusSubscriber(modid = FlexiBookMod.MOD_ID)
*///?}
//?}
public final class CommonModEvents {
    private CommonModEvents() {}

    //? if neoforge {
    /*@SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FlexiBookCommands.register(event.getDispatcher());
    }
    *///?} else {
    //? if forge {
    /*@SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FlexiBookCommands.register(event.getDispatcher());
    }
    *///?}
    //?}
}
