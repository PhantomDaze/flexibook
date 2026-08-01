package io.github.PhantomDaze.flexibook.util;

import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Open resource as UTF-8 reader across MC versions. */
public final class ResourceIo {
    private ResourceIo() {}

    public static BufferedReader openAsReader(Resource resource) throws IOException {
        //? if >=1.21 {
        return resource.openAsReader();
        //?} else
        /*return new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8));*/
    }
}
