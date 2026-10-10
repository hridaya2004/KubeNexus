package dev.hridaya.kubenexus.core.common.result

sealed interface Result<out T> {
    data class Success<out T>(val data: T) : Result<T>
    data class Error(val error: AppError) : Result<Nothing>
    data object Loading : Result<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isError: Boolean
        get() = this is Error

    val isFailure: Boolean
        get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw (error as? AppError.Unknown)?.throwable ?: RuntimeException(error.message)
        is Loading -> throw IllegalStateException("Result is still Loading")
    }

    fun exceptionOrNull(): AppError? = when (this) {
        is Error -> error
        else -> null
    }
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> Result.Error(error)
    is Result.Loading -> Result.Loading
}

inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
    is Result.Success -> transform(data)
    is Result.Error -> Result.Error(error)
    is Result.Loading -> Result.Loading
}

inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T> Result<T>.onError(action: (AppError) -> Unit): Result<T> {
    if (this is Result.Error) action(error)
    return this
}
