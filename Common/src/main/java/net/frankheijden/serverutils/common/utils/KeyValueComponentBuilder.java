package net.frankheijden.serverutils.common.utils;

import java.util.ArrayList;
import java.util.List;
import net.frankheijden.serverutils.common.config.MessagesResource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class KeyValueComponentBuilder {

    private final MessagesResource.Message format;
    private final List<TagResolver[]> templatesList;
    private final String keyPlaceholder;
    private final String valuePlaceholder;

    private KeyValueComponentBuilder(
            MessagesResource.Message format,
            String keyPlaceholder,
            String valuePlaceholder
    ) {
        this.format = format;
        this.templatesList = new ArrayList<>();
        this.keyPlaceholder = keyPlaceholder;
        this.valuePlaceholder = valuePlaceholder;
    }

    /**
     * Constructs a new KeyValueComponentBuilder.
     */
    public static KeyValueComponentBuilder create(
            MessagesResource.Message format,
            String keyPlaceholder,
            String valuePlaceholder
    ) {
        return new KeyValueComponentBuilder(format, keyPlaceholder, valuePlaceholder);
    }

    public KeyValueComponentBuilder.KeyValuePair key(String key) {
        return new KeyValuePair(key);
    }

    public KeyValueComponentBuilder.KeyValuePair key(Component key) {
        return new KeyValuePair(key);
    }

    private KeyValueComponentBuilder add(TagResolver key, TagResolver value) {
        this.templatesList.add(new TagResolver[]{ key, value });
        return this;
    }

    /**
     * Builds the current ListMessageBuilder instance into a Component.
     */
    public List<Component> build() {
        List<Component> components = new ArrayList<>(templatesList.size());

        for (TagResolver[] templates : templatesList) {
            components.add(format.toComponent(templates));
        }

        return components;
    }

    public class KeyValuePair {

        private final TagResolver key;

        private KeyValuePair(String key) {
            this.key = TagResolver.resolver(Placeholder.parsed(keyPlaceholder, key));
        }

        private KeyValuePair(Component key) {
            this.key = TagResolver.resolver(Placeholder.component(keyPlaceholder, key));
        }

        public KeyValueComponentBuilder value(String value) {
            if (value == null) return KeyValueComponentBuilder.this;
            return add(key, TagResolver.resolver(Placeholder.parsed(valuePlaceholder, value)));
        }

        public KeyValueComponentBuilder value(Component value) {
            if (value == null) return KeyValueComponentBuilder.this;
            return add(key, TagResolver.resolver(Placeholder.component(valuePlaceholder, value)));
        }
    }
}
