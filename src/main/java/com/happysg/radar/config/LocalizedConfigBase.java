package com.happysg.radar.config;

import com.happysg.radar.CreateRadar;
import net.createmod.catnip.config.ConfigBase;
import net.neoforged.neoforge.common.ModConfigSpec;

public abstract class LocalizedConfigBase extends ConfigBase {
    private static final String KEY_PREFIX = CreateRadar.MODID + ".configuration.";

    private static String translationKey(String name) {
        return KEY_PREFIX + name;
    }

    @Override
    protected ConfigBool b(boolean current, String name, String... comment) {
        return new LocalizedConfigBool(this, name, current, translationKey(name), comment);
    }

    @Override
    protected ConfigFloat f(float current, float min, float max, String name, String... comment) {
        return new LocalizedConfigFloat(this, name, current, min, max, translationKey(name), comment);
    }

    @Override
    protected ConfigFloat f(float current, float min, String name, String... comment) {
        return f(current, min, Float.MAX_VALUE, name, comment);
    }

    @Override
    protected ConfigInt i(int current, int min, int max, String name, String... comment) {
        return new LocalizedConfigInt(this, name, current, min, max, translationKey(name), comment);
    }

    @Override
    protected ConfigInt i(int current, int min, String name, String... comment) {
        return i(current, min, Integer.MAX_VALUE, name, comment);
    }

    @Override
    protected ConfigInt i(int current, String name, String... comment) {
        return i(current, Integer.MIN_VALUE, Integer.MAX_VALUE, name, comment);
    }

    @Override
    protected <T extends Enum<T>> ConfigEnum<T> e(T current, String name, String... comment) {
        return new LocalizedConfigEnum<>(this, name, current, translationKey(name), comment);
    }

    @Override
    protected ConfigGroup group(int depth, String name, String... comment) {
        return new LocalizedConfigGroup(this, name, depth, translationKey(name), comment);
    }

    private interface LocalizedValue {
        String translationKey();

        default void addTranslation(ModConfigSpec.Builder builder) {
            builder.translation(translationKey());
        }
    }

    private static class LocalizedConfigBool extends ConfigBool implements LocalizedValue {
        private final String translationKey;

        private LocalizedConfigBool(ConfigBase owner, String name, boolean current, String translationKey, String... comment) {
            owner.super(name, current, comment);
            this.translationKey = translationKey;
        }

        @Override
        public String translationKey() {
            return translationKey;
        }

        @Override
        public void register(ModConfigSpec.Builder builder) {
            addTranslation(builder);
            super.register(builder);
        }
    }

    private static class LocalizedConfigFloat extends ConfigFloat implements LocalizedValue {
        private final String translationKey;

        private LocalizedConfigFloat(ConfigBase owner, String name, float current, float min, float max,
                                     String translationKey, String... comment) {
            owner.super(name, current, min, max, comment);
            this.translationKey = translationKey;
        }

        @Override
        public String translationKey() {
            return translationKey;
        }

        @Override
        public void register(ModConfigSpec.Builder builder) {
            addTranslation(builder);
            super.register(builder);
        }
    }

    private static class LocalizedConfigInt extends ConfigInt implements LocalizedValue {
        private final String translationKey;

        private LocalizedConfigInt(ConfigBase owner, String name, int current, int min, int max,
                                   String translationKey, String... comment) {
            owner.super(name, current, min, max, comment);
            this.translationKey = translationKey;
        }

        @Override
        public String translationKey() {
            return translationKey;
        }

        @Override
        public void register(ModConfigSpec.Builder builder) {
            addTranslation(builder);
            super.register(builder);
        }
    }

    private static class LocalizedConfigEnum<T extends Enum<T>> extends ConfigEnum<T> implements LocalizedValue {
        private final String translationKey;

        private LocalizedConfigEnum(ConfigBase owner, String name, T current, String translationKey, String... comment) {
            owner.super(name, current, comment);
            this.translationKey = translationKey;
        }

        @Override
        public String translationKey() {
            return translationKey;
        }

        @Override
        public void register(ModConfigSpec.Builder builder) {
            addTranslation(builder);
            super.register(builder);
        }
    }

    private static class LocalizedConfigGroup extends ConfigGroup implements LocalizedValue {
        private final String translationKey;

        private LocalizedConfigGroup(ConfigBase owner, String name, int depth, String translationKey, String... comment) {
            owner.super(name, depth, comment);
            this.translationKey = translationKey;
        }

        @Override
        public String translationKey() {
            return translationKey;
        }

        @Override
        public void register(ModConfigSpec.Builder builder) {
            addTranslation(builder);
            super.register(builder);
        }
    }
}
