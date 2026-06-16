package com.nexus.ide.core.utils

/**
 * Minimal Result type used by the domain layer. Sealed so the compiler
 * can enforce exhaustive handling at call sites.
 */
sealed interface NxResult<out T> {
    data class Ok<T>(val value: T) : NxResult<T>
    data class Err(val cause: Throwable, val message: String? = null) : NxResult<Nothing>

    fun isOk(): Boolean = this is Ok
    fun isErr(): Boolean = this is Err

    fun getOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): Throwable? = (this as? Err)?.cause
}

inline fun <T> runNx(block: () -> T): NxResult<T> = try {
    NxResult.Ok(block())
} catch (t: Throwable) {
    NxResult.Err(t, t.message)
}

inline fun <T, R> NxResult<T>.map(block: (T) -> R): NxResult<R> = when (this) {
    is NxResult.Ok -> NxResult.Ok(block(value))
    is NxResult.Err -> this
}
