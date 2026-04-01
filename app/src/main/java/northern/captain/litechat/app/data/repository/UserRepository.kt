package northern.captain.litechat.app.data.repository

import android.content.Context
import coil.Coil
import coil.memory.MemoryCache
import dagger.hilt.android.qualifiers.ApplicationContext
import northern.captain.litechat.app.config.ApiConfig
import northern.captain.litechat.app.data.local.dao.UserDao
import northern.captain.litechat.app.data.local.entity.UserEntity
import northern.captain.litechat.app.data.remote.LiteChatApi
import northern.captain.litechat.app.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val api: LiteChatApi,
    private val userDao: UserDao,
    @ApplicationContext private val context: Context
) {
    fun getUsers(): Flow<List<User>> {
        return userDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun refreshUsers() {
        val oldUsers = userDao.getAllOnce()
        val oldAvatars = oldUsers.associate { it.userId to it.avatar }

        val response = api.getUsers()
        val entities = response.users.map { dto ->
            UserEntity(
                userId = dto.userId,
                name = dto.name,
                avatar = dto.avatar
            )
        }
        userDao.insertAll(entities)

        // Invalidate Coil cache for users whose avatar changed
        val imageLoader = Coil.imageLoader(context)
        for (entity in entities) {
            val oldAvatar = oldAvatars[entity.userId]
            if (oldAvatar != null && oldAvatar != entity.avatar) {
                // Remove old cached URL (includes old filename as query param)
                val oldUrl = "${ApiConfig.BASE_URL}/litechat/api/v1/users/${entity.userId}/avatar?f=$oldAvatar"
                imageLoader.diskCache?.remove(oldUrl)
                imageLoader.memoryCache?.remove(MemoryCache.Key(oldUrl))
            }
        }
    }

    suspend fun getUser(userId: Long): User? {
        return userDao.getById(userId)?.toDomain()
    }

    suspend fun getUserName(userId: Long): String {
        return userDao.getById(userId)?.name ?: "Unknown"
    }

    private fun UserEntity.toDomain() = User(
        userId = userId,
        name = name,
        avatar = avatar
    )
}
