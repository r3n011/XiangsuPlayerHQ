# 修复 Hilt 依赖注入错误 (TranscodeCacheDao)

## 问题描述
构建失败，Hilt 编译报错：`com.theveloper.pixelplay.data.database.TranscodeCacheDao is injected at ... TranscodeCacheViewModel`。

**原因**：`TranscodeCacheDao` 虽然在 `PixelPlayDatabase` 中定义了抽象方法，但没有在 Hilt 依赖注入模块 (`AppModule.kt`) 中提供 `@Provides` 方法，导致 Hilt 无法将其注入到 `TranscodeCacheViewModel` 中。

## 修改计划

### 文件 1: `e:\PixelPlayer-master\app\src\main\java\com\theveloper\pixelplay\di\AppModule.kt`

**修改内容**:
1.  添加 `TranscodeCacheDao` 的 import。
2.  添加一个 `@Provides` 方法来提供 `TranscodeCacheDao` 实例。

```kotlin
// 1. 添加 import
import com.theveloper.pixelplay.data.database.TranscodeCacheDao

// 2. 在 @Module 类中添加 @Provides 方法
@Provides
fun provideTranscodeCacheDao(database: PixelPlayDatabase): TranscodeCacheDao {
    return database.transcodeCacheDao()
}
```

### 文件 2: `e:\PixelPlayer-master\app\src\main\java\com\theveloper\pixelplay\utils\TranscodeCacheManager.kt` (可选)
如果 `TranscodeCacheManager` 中有静态方法需要依赖 DAO，确保它能正确获取 DAO。

## 风险与考虑
- 此修改仅涉及添加 Hilt 的依赖注入绑定，不会改变业务逻辑。
- 不需要修改数据库结构或迁移脚本，因为 `TranscodeCacheEntity` 已经在 `PixelPlayDatabase` 的 entities 列表中。

## 后续步骤
- 修改完成后，重新编译项目以确认问题已解决。
