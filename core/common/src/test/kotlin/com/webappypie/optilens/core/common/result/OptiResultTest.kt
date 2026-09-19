package com.webappypie.optilens.core.common.result

import org.junit.Assert.*
import org.junit.Test

class OptiResultTest {

    @Test
    fun `success carries data`() {
        val result = OptiResult.Success("hello")
        assertEquals("hello", result.data)
        assertFalse(result.isError)
        assertFalse(result.isLoading)
    }

    @Test
    fun `error carries OptiError`() {
        val error = OptiError.Unknown(message = "test error")
        val result = OptiResult.Error(error)
        assertTrue(result.isError)
        assertEquals(error, (result as OptiResult.Error).error)
    }

    @Test
    fun `loading carries optional fraction`() {
        val result = OptiResult.Loading(0.5f)
        assertTrue(result.isLoading)
        assertEquals(0.5f, (result as OptiResult.Loading).fraction)
    }

    @Test
    fun `onSuccess runs block for success`() {
        var called = false
        OptiResult.Success(42).onSuccess { called = true }
        assertTrue(called)
    }

    @Test
    fun `onSuccess does not run for error`() {
        var called = false
        OptiResult.Error(OptiError.Unknown()).onSuccess { called = true }
        assertFalse(called)
    }

    @Test
    fun `map transforms success value`() {
        val result = OptiResult.Success(10).map { it * 2 }
        assertEquals(20, (result as OptiResult.Success).data)
    }

    @Test
    fun `map passes error through unchanged`() {
        val error = OptiError.StorageFailed()
        val result: OptiResult<Int> = OptiResult.Error(error)
        val mapped = result.map { it * 2 }
        assertTrue(mapped.isError)
    }

    @Test
    fun `getOrNull returns data for success`() {
        assertEquals("data", OptiResult.Success("data").getOrNull())
    }

    @Test
    fun `getOrNull returns null for error`() {
        assertNull(OptiResult.Error(OptiError.Unknown()).getOrNull())
    }

    @Test
    fun `OptiError displayMessage is safe for user display`() {
        val e = OptiError.CameraUnavailable(reason = "in use")
        assertTrue(e.displayMessage.isNotBlank())
        // Must not contain stack traces or internal paths
        assertFalse(e.displayMessage.contains("Exception"))
        assertFalse(e.displayMessage.contains(".kt:"))
    }
}
