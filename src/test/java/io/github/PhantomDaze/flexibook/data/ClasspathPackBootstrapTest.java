package io.github.PhantomDaze.flexibook.data;

import io.github.PhantomDaze.flexibook.api.FlexiBookAPI;
import io.github.PhantomDaze.flexibook.client.theme.BookContentRegistry;
import io.github.PhantomDaze.flexibook.client.theme.BookDefinitionRegistry;
import io.github.PhantomDaze.flexibook.client.theme.BookThemeRegistry;
import io.github.PhantomDaze.flexibook.content.AdaptiveBookContent;
import io.github.PhantomDaze.flexibook.util.FlexiBookIds;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验 jar 内置的示例包可从 classpath 注册，使服务端 {@code /flexibook give} 不依赖客户端资源重载。
 */
class ClasspathPackBootstrapTest {

    @BeforeEach
    void clear() {
        BookContentRegistry.clear();
        BookDefinitionRegistry.clear();
        // 主题保留内置项，只清理可能残留的额外注册
        BookThemeRegistry.bootstrap();
    }

    @AfterEach
    void tearDown() {
        BookContentRegistry.clear();
        BookDefinitionRegistry.clear();
        BookThemeRegistry.bootstrap();
    }

    @Test
    void fieldNotesSampleRegistersThemeContentAndBook() {
        ClasspathPackBootstrap.loadFieldNotesSample();

        Identifier journal = FlexiBookIds.of("fieldnotes", "journal");

        assertTrue(BookThemeRegistry.isRegistered(FlexiBookIds.of("fieldnotes", "parchment")),
                "fieldnotes:parchment 主题必须从 classpath 注册");
        assertTrue(BookContentRegistry.isRegistered(journal),
                "fieldnotes:journal 内容必须从 classpath 注册");
        assertTrue(BookDefinitionRegistry.isRegistered(journal),
                "fieldnotes:journal 书籍定义必须从 classpath 注册");

        AdaptiveBookContent content = FlexiBookAPI.resolveBook(journal);
        assertFalse(content.isEmpty(), "fieldnotes:journal 的正文必须能从 classpath 内容解析");
    }

    @Test
    void bundledSamplesRegisterDemoGuideAndJournal() {
        ClasspathPackBootstrap.loadBundledSamples();

        Identifier demo = FlexiBookIds.of("flexibook", "demo_guide");
        Identifier journal = FlexiBookIds.of("fieldnotes", "journal");

        assertTrue(BookDefinitionRegistry.isRegistered(demo),
                "demo_guide 必须从 classpath 注册（与创意标签/资源包保持一致）");
        assertTrue(BookDefinitionRegistry.isRegistered(journal),
                "fieldnotes:journal 必须从 classpath 注册");

        AdaptiveBookContent demoContent = FlexiBookAPI.resolveBook(demo);
        assertFalse(demoContent.isEmpty(), "demo_guide 正文必须能从 classpath 内容解析");

        AdaptiveBookContent journalContent = FlexiBookAPI.resolveBook(journal);
        assertFalse(journalContent.isEmpty(), "fieldnotes:journal 正文必须能从 classpath 内容解析");
    }

}
