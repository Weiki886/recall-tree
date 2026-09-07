package com.recalltree.domain;

import java.util.UUID;

/**
 * MemoryItem 的稳定身份标识。
 *
 * <p>领域模型定义：{@code id} 为 UUIDv7，跨版本不变。
 * 本值对象封装身份验证逻辑，确保 id 不会在 domain 层被误用为其他类型。
 * 不可变。equals/hashCode 基于内部 UUID。
 */
public record MemoryItemId(UUID value) {

    public MemoryItemId {
        if (value == null) {
            throw new IllegalArgumentException("MemoryItemId must not be null");
        }
    }

    public static MemoryItemId fromString(String s) {
        return new MemoryItemId(UUID.fromString(s));
    }

    /**
     * 创建新的随机 MemoryItemId。
     *
     * <p>TODO: 当前使用 {@link UUID#randomUUID()}（UUIDv4，随机），
     * 后续需替换为 UUIDv7（时间有序）以支持键集分页（WHERE id &lt; ? ORDER BY id DESC）。
     * 可使用 {@code com.github.f4b6a3:uuid-creator} 库生成 UUIDv7，
     * 或将生成逻辑委托给 {@code IdGeneratorPort}。
     */
    public static MemoryItemId random() {
        // TODO: 替换为 UUIDv7 生成器
        return new MemoryItemId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
