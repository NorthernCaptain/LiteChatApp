package northern.captain.litechat.app.data.repository

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
    private val userDao: UserDao
) {
    fun getUsers(): Flow<List<User>> {
        return userDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun refreshUsers() {
        val response = api.getUsers()
        val entities = response.users.map { dto ->
            UserEntity(
                userId = dto.userId,
                name = dto.name,
                avatar = dto.avatar
            )
        }
        userDao.insertAll(entities)
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
